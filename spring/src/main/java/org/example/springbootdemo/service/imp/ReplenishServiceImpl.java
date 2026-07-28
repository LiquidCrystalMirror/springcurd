package org.example.springbootdemo.service.imp;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springbootdemo.config.TestModeContext;
import org.example.springbootdemo.util.ApiResult;
import org.example.springbootdemo.dto.ReplenishApproveDTO;
import org.example.springbootdemo.dto.ReplenishDTO;
import org.example.springbootdemo.dto.ReplenishItemDTO;
import org.example.springbootdemo.entity.Product;
import org.example.springbootdemo.entity.ProductStock;
import org.example.springbootdemo.entity.ReplenishOrder;
import org.example.springbootdemo.entity.StockReplenishLog;
import org.example.springbootdemo.mapper.ProductMapper;
import org.example.springbootdemo.mapper.ProductStockMapper;
import org.example.springbootdemo.mapper.ReplenishOrderMapper;
import org.example.springbootdemo.mapper.StockReplenishLogMapper;

import org.example.springbootdemo.service.ReplenishService;
import org.example.springbootdemo.util.LuaScriptManager;
import org.example.springbootdemo.util.StructuredLogger;
import org.example.springbootdemo.vo.ReplenishOrderVO;
import org.example.springbootdemo.vo.ReplenishVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

import static org.example.springbootdemo.util.StructuredLogger.LogCategory.*;

@Slf4j
@Service
public class ReplenishServiceImpl implements ReplenishService {

    @Resource
    private LuaScriptManager luaScriptManager;
    @Resource
    private StockReplenishLogMapper replenishLogMapper;
    @Resource
    private ReplenishOrderMapper replenishOrderMapper;
    @Resource
    private ProductStockMapper productStockMapper;
    @Resource
    private ProductMapper productMapper;
    @Resource
    private RabbitMQDemoProducer rabbitMQDemoProducer;

    @Override
    public ApiResult<ReplenishVO> replenish(ReplenishDTO dto) {
        // 0. 验证商品名称（如果前端传入了商品名称）
        ApiResult<Void> validationError = validateProductNames(dto.getItems());
        if (validationError != null) {
            return ApiResult.error(validationError.getCode(), validationError.getMessage());
        }
        
        // 提取商品ID到名称的映射，供后续创建商品时使用
        Map<Long, String> productNameMap = new HashMap<>();
        for (ReplenishItemDTO item : dto.getItems()) {
            if (item.getProductName() != null && !item.getProductName().isEmpty()) {
                productNameMap.put(item.getProductId(), item.getProductName());
            }
        }
        
        // 检查是否为测试模式
        if (TestModeContext.isTestMode()) {
            StructuredLogger.info(REPLENISH_REDIS, "SYSTEM", 
                    "【测试模式】补货请求，不执行Redis和数据库操作");
            
            // 聚合数量
            Map<Long, Integer> operations = new HashMap<>();
            for (ReplenishItemDTO item : dto.getItems()) {
                operations.merge(item.getProductId(), item.getQuantity(), Integer::sum);
            }
            
            // 生成模拟批次ID
            Long batchId = IdWorker.getId();
            
            // 返回模拟结果
            ReplenishVO mockVO = new ReplenishVO();
            mockVO.setId(batchId);
            mockVO.setOperations(operations);
            
            return ApiResult.success(mockVO);
        }
        
        // 1. 聚合数量
        Map<Long, Integer> operations = new HashMap<>();
        for (ReplenishItemDTO item : dto.getItems()) {
            operations.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        if (operations.isEmpty()) {
            return ApiResult.error(400, "补货商品列表不能为空");
        }

        // 2. 生成雪花批次ID (使用 MyBatis-Plus 内置工具，无需额外依赖)
        Long batchId = IdWorker.getId();
        StructuredLogger.info(REPLENISH_REDIS, "BATCH-" + batchId, 
                "开始执行补货操作，商品种类数={}", operations.size());

        // 2.1 创建补货单头记录（审核流程追踪）
        try {
            ReplenishOrder order = new ReplenishOrder();
            order.setId(batchId);
            order.setCreatorId(TestModeContext.isTestMode() ? 1 : getCurrentUserId());
            order.setStatus(0); // 待审核
            order.setCreateTime(LocalDateTime.now());
            replenishOrderMapper.insert(order);
            StructuredLogger.info(REPLENISH_MYSQL, "BATCH-" + batchId, "补货单头记录创建成功，status=待审核");
        } catch (Exception e) {
            StructuredLogger.warn(REPLENISH_MYSQL, "BATCH-" + batchId,
                    "补货单头记录创建失败（不影响主流程）: {}", e.getMessage());
        }

        // 3. 执行 Lua 脚本
        LuaScriptManager.ReplenishResult luaResult;
        try {
            StructuredLogger.debug(REPLENISH_REDIS, "BATCH-" + batchId, 
                    "调用Lua脚本执行批量库存增加");
            luaResult = luaScriptManager.executeReplenish(operations);
        } catch (Exception e) {
            StructuredLogger.error(REPLENISH_REDIS, "BATCH-" + batchId, 
                    "补货Redis执行异常，可能原因：Redis连接断开、Lua脚本错误或网络超时", e);
            return ApiResult.error(500, "补货失败，Redis操作异常");
        }

        if (!luaResult.isSuccess()) {
            StructuredLogger.warn(REPLENISH_REDIS, "BATCH-" + batchId, 
                    "补货Redis操作失败，错误信息={}", luaResult.getMessage());
            return ApiResult.error(400, luaResult.getMessage());
        }

        // RabbitMQ: 发送补货增加消息（异步，不影响主流程）
        try {
            rabbitMQDemoProducer.sendAdd(String.valueOf(batchId), operations);
        } catch (Exception rabbitEx) {
            StructuredLogger.warn(REPLENISH_REDIS, "BATCH-" + batchId,
                    "RabbitMQ补货消息发送异常（不影响业务）: {}", rabbitEx.getMessage());
        }

        // 4. 构建审计日志并同步更新 product_stock 表
        List<StockReplenishLog> logs = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : operations.entrySet()) {
            StockReplenishLog log = new StockReplenishLog();
            log.setId(batchId);  // 使用雪花ID
            log.setProductId(e.getKey());
            log.setQuantity(e.getValue());
            log.setStatus(1);
            LuaScriptManager.ReplenishResult.StockChange change = luaResult.getChanges().get(e.getKey());
            if (change != null) {
                log.setStockBefore(change.getBefore());
                log.setStockAfter(change.getAfter());
                String productName = productNameMap.get(e.getKey());
                updateProductStock(e.getKey(), change.getAfter(), productName);
            }
            logs.add(log);
        }

        // 5. 写入补货审计日志（重试机制）
        StructuredLogger.info(REPLENISH_MYSQL, "BATCH-" + batchId, 
                "开始写入补货审计日志，记录数={}", logs.size());
        
        boolean dbOk = false;
        int maxRetries = 2;
        for (int i = 0; i < maxRetries; i++) {
            try {
                replenishLogMapper.batchInsert(logs);
                dbOk = true;
                StructuredLogger.info(REPLENISH_MYSQL, "BATCH-" + batchId, "补货审计日志写入成功");
                break;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                dbOk = true;
                StructuredLogger.info(REPLENISH_MYSQL, "BATCH-" + batchId, 
                        "补货日志已存在，跳过插入（联合主键幂等保护）");
                break;
            } catch (Exception e) {
                StructuredLogger.warn(REPLENISH_MYSQL, "BATCH-" + batchId, 
                        "补货日志写入失败，第{}/{}次重试", i + 1, maxRetries, e);
                try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }

        if (!dbOk) {
            StructuredLogger.error(REPLENISH_MYSQL, "BATCH-" + batchId, 
                    "【严重错误-需人工介入】补货审计日志最终写入失败，Redis已增加但数据库未记录，数据不一致风险！请立即检查并手动修复。可能原因：数据库连接永久断开或唯一约束持续冲突。");
        }

        ReplenishVO vo = new ReplenishVO();
        vo.setId(batchId);
        vo.setOperations(operations);
        
        StructuredLogger.info(REPLENISH_MYSQL, "BATCH-" + batchId, "补货流程结束");
        return ApiResult.success(vo);
    }

    /**
     * 更新商品库存（不存在则创建）
     * @param productId 商品ID
     * @param newStock 新库存值
     * @param productName 商品名称（可选，用于新建商品时）
     */
    private void updateProductStock(Long productId, int newStock, String productName) {
        ProductStock stock = productStockMapper.queryById(productId);
        if (stock == null) {
            StructuredLogger.info(REPLENISH_MYSQL, "SYSTEM",
                    "商品库存记录不存在，自动创建新记录，productId={}, stock={}", productId, newStock);
            
            // 1. 先创建 product_stock 记录（因为 product 表有外键依赖）
            stock = new ProductStock();
            stock.setProductId(productId);
            stock.setStock(newStock);
            stock.setVersion(0);
            stock.setUpdateTime(LocalDateTime.now());
            productStockMapper.insert(stock);
            StructuredLogger.info(REPLENISH_MYSQL, "SYSTEM",
                    "商品库存记录创建成功，productId={}, stock={}", productId, newStock);
            
            // 2. 检查并创建 product 记录（如果不存在）
            Product product = productMapper.queryById(productId);
            if (product == null) {
                StructuredLogger.info(REPLENISH_MYSQL, "SYSTEM",
                        "商品信息不存在，自动创建新商品记录，productId={}", productId);
                
                Product newProduct = new Product();
                newProduct.setProductId(productId);
                // 使用前端传入的商品名称，如果没有则使用默认名称
                String finalProductName = (productName != null && !productName.isEmpty()) 
                        ? productName : "商品_" + productId;
                newProduct.setProductName(finalProductName);
                productMapper.insert(newProduct);
                
                StructuredLogger.info(REPLENISH_MYSQL, "SYSTEM",
                        "商品信息创建成功，productId={}, productName={}", productId, finalProductName);
            }
            
            return;
        }

        // 存在则更新（使用乐观锁）
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            int currentVersion = stock.getVersion();
            int rows = productStockMapper.updateStockWithVersion(productId, newStock, currentVersion);
            if (rows > 0) {
                StructuredLogger.debug(REPLENISH_MYSQL, "SYSTEM",
                        "库存备份表更新成功，productId={}, version={}", productId, currentVersion);
                return;
            }

            StructuredLogger.warn(REPLENISH_MYSQL, "SYSTEM",
                    "乐观锁冲突，重试 {}/{}，productId={}，可能原因：并发更新导致版本不一致",
                    i + 1, maxRetries, productId);
            if (i < maxRetries - 1) {
                try { Thread.sleep(10L * (i + 1)); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    StructuredLogger.error(REPLENISH_MYSQL, "SYSTEM",
                            "库存更新重试被中断，productId={}", productId, e);
                    throw new RuntimeException("重试中断", e);
                }
                stock = productStockMapper.queryById(productId);
                if (stock == null) {
                    StructuredLogger.error(REPLENISH_MYSQL, "SYSTEM",
                            "库存记录不存在，productId={}，可能原因：记录被其他事务删除", productId);
                    throw new RuntimeException("记录不存在: " + productId);
                }
            }
        }
        StructuredLogger.error(REPLENISH_MYSQL, "SYSTEM",
                "【严重错误-需人工介入】补货时库存备份表更新最终失败（已重试{}次），productId={}，数据不一致风险！请立即检查并手动修复。可能原因：高并发场景下持续冲突",
                maxRetries, productId);
        throw new RuntimeException("乐观锁重试失败，productId=" + productId);
    }
    
    /**
     * 验证商品名称是否与数据库一致
     * @param items 补货商品列表
     * @return 如果有错误返回 ApiResult.error，否则返回 null
     */
    private ApiResult<Void> validateProductNames(List<ReplenishItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        
        Map<Long, String> invalidProducts = new HashMap<>();
        
        for (ReplenishItemDTO item : items) {
            Long productId = item.getProductId();
            String productName = item.getProductName();
            
            // 如果前端传入了商品名称，需要验证
            if (productName != null && !productName.isEmpty()) {
                Product product = productMapper.queryById(productId);
                
                if (product == null) {
                    // 商品不存在，允许补货（会自动创建）
                    StructuredLogger.info(REPLENISH_REDIS, "SYSTEM",
                            "商品不存在，将在补货时自动创建，productId={}, productName={}", productId, productName);
                } else if (!productName.equals(product.getProductName())) {
                    // 商品名称不匹配，记录错误
                    invalidProducts.put(productId, product.getProductName());
                    StructuredLogger.warn(REPLENISH_REDIS, "SYSTEM",
                            "商品名称不匹配，输入：{}，数据库：{}，productId={}",
                            productName, product.getProductName(), productId);
                }
            }
        }
        
        // 如果有商品名称不匹配，返回错误
        if (!invalidProducts.isEmpty()) {
            StringBuilder errorMsg = new StringBuilder("以下商品名称与数据库不一致：");
            for (Map.Entry<Long, String> entry : invalidProducts.entrySet()) {
                errorMsg.append("\n商品ID: ").append(entry.getKey())
                        .append(", 数据库中的名称: ").append(entry.getValue());
            }
            return ApiResult.error(400, errorMsg.toString());
        }
        
        return null;
    }

    // ==================== 补货单审核 ====================

    @Override
    public ApiResult<PageInfo<ReplenishOrderVO>> findPage(int page, int pageSize, Integer status) {
        int offset = (page - 1) * pageSize;
        List<ReplenishOrderVO> list = replenishOrderMapper.selectPageWithDetail(status, offset, pageSize);
        long total = replenishOrderMapper.countWithDetail(status);

        PageInfo<ReplenishOrderVO> pageInfo = new PageInfo<>();
        pageInfo.setList(list);
        pageInfo.setTotal(total);
        pageInfo.setPageNum(page);
        pageInfo.setPageSize(pageSize);
        pageInfo.setPages((int) Math.ceil((double) total / pageSize));
        return ApiResult.success(pageInfo);
    }

    @Override
    public ApiResult<Void> approve(ReplenishApproveDTO dto, Integer approverId) {
        if (approverId == null) {
            return ApiResult.error(401, "未登录");
        }

        Boolean approved = dto.getApproved();
        if (approved == null) {
            return ApiResult.error(400, "审核结果不能为空");
        }

        // 拒绝时必须填写理由
        if (!approved && (dto.getRemark() == null || dto.getRemark().trim().isEmpty())) {
            return ApiResult.error(400, "拒绝时必须填写理由");
        }

        int targetStatus = approved ? 1 : 2; // 1=审核通过, 2=审核拒绝
        String remark = dto.getRemark() != null ? dto.getRemark().trim() : (approved ? "审核通过" : "");

        int rows = replenishOrderMapper.approve(dto.getId(), targetStatus, approverId, remark);
        if (rows > 0) {
            StructuredLogger.info(REPLENISH_MYSQL, "BATCH-" + dto.getId(),
                    "补货单审核完成，结果={}, 审核人={}", approved ? "通过" : "拒绝", approverId);
            return ApiResult.success();
        }

        // rows=0 说明该单不存在或已被他人审核（乐观锁 WHERE status=0 条件不满足）
        StructuredLogger.warn(REPLENISH_MYSQL, "BATCH-" + dto.getId(),
                "补货单审核失败（可能已被他人审核或单不存在），审核人={}", approverId);
        return ApiResult.error(409, "该补货单已被审核或不存在");
    }

    /**
     * 获取当前登录用户ID（从请求上下文获取，测试模式下返回 1）
     */
    private Integer getCurrentUserId() {
        try {
            jakarta.servlet.http.HttpServletRequest request =
                    ((org.springframework.web.context.request.ServletRequestAttributes)
                            org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes())
                            .getRequest();
            Integer userId = (Integer) request.getAttribute("userId");
            return userId != null ? userId : 1; // 兜底
        } catch (Exception e) {
            return 1; // 非Web上下文兜底
        }
    }
}