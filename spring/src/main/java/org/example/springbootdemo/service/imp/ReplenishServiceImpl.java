package org.example.springbootdemo.service.imp;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springbootdemo.dto.ApiResult;
import org.example.springbootdemo.dto.ReplenishDTO;
import org.example.springbootdemo.dto.ReplenishItemDTO;
import org.example.springbootdemo.entity.ProductStock;
import org.example.springbootdemo.entity.StockReplenishLog;
import org.example.springbootdemo.mapper.ProductStockMapper;
import org.example.springbootdemo.mapper.StockReplenishLogMapper;
import org.example.springbootdemo.service.ReplenishService;
import org.example.springbootdemo.util.LuaScriptManager;
import org.example.springbootdemo.util.StructuredLogger;
import org.example.springbootdemo.vo.ReplenishVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private ProductStockMapper productStockMapper;

    @Override
    public ApiResult<ReplenishVO> replenish(ReplenishDTO dto) {
        String replenishNo = dto.getReplenishNo();

        // 聚合
        Map<Long, Integer> operations = new HashMap<>();
        for (ReplenishItemDTO item : dto.getItems()) {
            operations.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }

        if (replenishNo == null || replenishNo.trim().isEmpty()) {
            return ApiResult.error(400, "补货单号不能为空");
        }
        if (operations.isEmpty()) {
            return ApiResult.error(400, "补货商品列表不能为空");
        }

        StructuredLogger.info(REPLENISH_REDIS, replenishNo, 
                "开始执行补货操作，商品种类数={}", operations.size());

        // 执行 Lua 脚本
        LuaScriptManager.ReplenishResult luaResult;
        try {
            StructuredLogger.debug(REPLENISH_REDIS, replenishNo, 
                    "调用Lua脚本执行批量库存增加");
            luaResult = luaScriptManager.executeReplenish(replenishNo, operations);
        } catch (Exception e) {
            StructuredLogger.error(REPLENISH_REDIS, replenishNo, 
                    "补货Redis执行异常，可能原因：Redis连接断开、Lua脚本错误或网络超时", e);
            return ApiResult.error(500, "补货失败，Redis操作异常");
        }

        if (!luaResult.isSuccess()) {
            StructuredLogger.warn(REPLENISH_REDIS, replenishNo, 
                    "补货Redis操作失败，错误信息={}，可能原因：幂等性检查失败或参数错误", 
                    luaResult.getMessage());
            return ApiResult.error(400, luaResult.getMessage());
        }

        StructuredLogger.info(REPLENISH_REDIS, replenishNo, 
                "补货Redis操作成功，商品种类数={}（不存在的商品已自动初始化）", operations.size());

        // 构建审计日志并同步更新 product_stock 表
        List<StockReplenishLog> logs = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : operations.entrySet()) {
            StockReplenishLog log = new StockReplenishLog();
            log.setReplenishNo(replenishNo);
            log.setProductId(e.getKey());
            log.setQuantity(e.getValue());
            log.setStatus(1);
            LuaScriptManager.ReplenishResult.StockChange change = luaResult.getChanges().get(e.getKey());
            if (change != null) {
                log.setStockBefore(change.getBefore());
                log.setStockAfter(change.getAfter());
                
                // 同步更新 product_stock 表（不存在则创建）
                updateProductStock(e.getKey(), change.getAfter());
            }
            logs.add(log);
        }

        // 写入补货审计日志（重试机制）
        StructuredLogger.info(REPLENISH_MYSQL, replenishNo, 
                "开始写入补货审计日志，记录数={}", logs.size());
        
        boolean dbOk = false;
        int maxRetries = 2;
        for (int i = 0; i < maxRetries; i++) {
            try {
                replenishLogMapper.batchInsert(logs);
                dbOk = true;
                StructuredLogger.info(REPLENISH_MYSQL, replenishNo, 
                        "补货审计日志写入成功");
                break;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 唯一约束冲突说明记录已存在，视为成功（幂等性保护）
                dbOk = true;
                StructuredLogger.info(REPLENISH_MYSQL, replenishNo, 
                        "补货日志已存在，跳过插入（幂等性保护）");
                break;
            } catch (Exception e) {
                StructuredLogger.warn(REPLENISH_MYSQL, replenishNo, 
                        "补货日志写入失败，第{}/{}次重试，可能原因：唯一约束冲突或连接超时", 
                        i + 1, maxRetries, e);
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            }
        }

        if (!dbOk) {
            StructuredLogger.error(REPLENISH_MYSQL, replenishNo, 
                    "【严重】补货日志最终写入失败，Redis已增加但数据库未记录，数据不一致风险！需人工介入检查。可能原因：数据库连接永久断开或未知异常。");
        }

        ReplenishVO vo = new ReplenishVO();
        vo.setReplenishNo(replenishNo);
        vo.setOperations(operations);
        
        StructuredLogger.info(REPLENISH_MYSQL, replenishNo, 
                "补货流程结束");
        return ApiResult.success(vo);
    }

    /**
     * 更新商品库存（不存在则创建）
     * @param productId 商品ID
     * @param newStock 新库存值
     */
    private void updateProductStock(Long productId, int newStock) {
        ProductStock stock = productStockMapper.queryById(productId);
        if (stock == null) {
            StructuredLogger.info(REPLENISH_MYSQL, "SYSTEM",
                    "商品库存记录不存在，自动创建新记录，productId={}, stock={}", productId, newStock);
            stock = new ProductStock();
            stock.setProductId(productId);
            stock.setStock(newStock);
            stock.setVersion(0);
            stock.setUpdateTime(LocalDateTime.now());
            productStockMapper.insert(stock);
            StructuredLogger.info(REPLENISH_MYSQL, "SYSTEM",
                    "商品库存记录创建成功，productId={}", productId);
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
                "乐观锁重试失败，productId={}，可能原因：高并发场景下持续冲突", productId);
        throw new RuntimeException("乐观锁重试失败，productId=" + productId);
    }
}