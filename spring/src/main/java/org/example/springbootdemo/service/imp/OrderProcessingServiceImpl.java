package org.example.springbootdemo.service.imp;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springbootdemo.config.StockScriptProperties;
import org.example.springbootdemo.constant.enums.ScriptResultEnum;
import org.example.springbootdemo.dto.ApiResult;
import org.example.springbootdemo.dto.OrderDTO;
import org.example.springbootdemo.dto.OrderItemDTO;
import org.example.springbootdemo.entity.OrderDetail;
import org.example.springbootdemo.entity.ProductStock;
import org.example.springbootdemo.mapper.ProductStockMapper;
import org.example.springbootdemo.service.OrderDetailService;
import org.example.springbootdemo.service.OrderProcessingService;
import org.example.springbootdemo.util.LuaScriptManager;
import org.example.springbootdemo.util.StructuredLogger;
import org.example.springbootdemo.vo.OrderVO;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.example.springbootdemo.util.StructuredLogger.LogCategory.*;

@Slf4j
@Service
public class OrderProcessingServiceImpl implements OrderProcessingService {
    @Resource
    private LuaScriptManager luaScriptManager;
    @Resource
    private OrderDetailService orderDetailService;
    @Resource
    private StockScriptProperties scriptProperties;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private ProductStockMapper productStockMapper;

    /**
     * 处理订单（批量扣减库存）
     * <p>业务流程：</p>
     * <ol>
     *   <li>合并相同商品的购买数量</li>
     *   <li>调用Lua脚本原子性扣减Redis库存</li>
     *   <li>扣减成功后返回订单信息</li>
     *   <li>订单明细通过消息队列异步持久化到数据库</li>
     * </ol>
     *
     * @param orderDTO 订单信息，包含订单号、平台ID和商品列表
     * @return 订单处理结果，成功时返回OrderVO对象，失败时返回错误信息
     */
    @Override
    public ApiResult<OrderVO> processOrder(OrderDTO orderDTO) {
        String bizNo = orderDTO.getOrderNo();
        String platformId = orderDTO.getPlatformId();
        // 合并相同商品的购买数量
        Map<Long, Integer> operations = new HashMap<>();
        for (OrderItemDTO item : orderDTO.getItems()) {
            operations.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }

        // 参数校验
        if (bizNo == null || bizNo.trim().isEmpty()) {
            return ApiResult.error(400, "订单号不能为空");
        }
        if (platformId == null || platformId.trim().isEmpty()) {
            return ApiResult.error(400, "平台ID不能为空");
        }
        if (operations.isEmpty()) {
            return ApiResult.error(400, "商品信息不能为空");
        }
        
        StructuredLogger.info(ORDER_REDIS, bizNo, 
                "开始处理订单，platformId={}, 商品种类数={}", platformId, operations.size());
        
        try {
            // 执行批量扣减（Lua脚本保证原子性）
            StructuredLogger.debug(ORDER_REDIS, bizNo, 
                    "调用Lua脚本执行批量库存扣减");
            LuaScriptManager.BatchResult result = luaScriptManager.executeBatchDeduct(bizNo, platformId, operations);

            if (result.isSuccess()) {
                // 检查是否为幂等命中（重复请求）
                String resultCode = result.getCode();
                String resultMessage = result.getMessage();
                
                if ("already_success".equals(resultMessage)) {
                    // 幂等命中：订单已处理过
                    StructuredLogger.info(ORDER_REDIS, bizNo, 
                            "订单已存在，幂等性拦截，请勿重复提交");
                    return ApiResult.error(409, "订单已存在，请勿重复提交");
                }
                
                // 首次成功：构造返回结果
                OrderVO orderVO = new OrderVO();
                orderVO.setOrderNo(bizNo);
                orderVO.setPlatformId(platformId);
                orderVO.setOperations(operations); // 设置聚合后的商品数量映射
                
                StructuredLogger.info(ORDER_REDIS, bizNo, 
                        "订单Redis库存扣减成功，商品种类数={}", operations.size());
                return ApiResult.success(orderVO);
            } else {
                // 转换错误消息为用户友好的提示
                String message = LuaScriptManager.getErrorMessage(result);
                StructuredLogger.warn(ORDER_REDIS, bizNo, 
                        "订单Redis库存扣减失败，错误码={}, 错误信息={}, 可能原因：库存不足或商品不存在", 
                        result.getCode(), message);
                return ApiResult.error(400, message);
            }
        } catch (Exception e) {
            // 捕获未预期的异常
            StructuredLogger.error(ORDER_REDIS, bizNo, 
                    "订单Redis操作发生未预期异常，可能原因：Redis连接断开、Lua脚本错误或网络超时", e);
            return ApiResult.error(500, "订单处理失败，请稍后重试");
        }
    }
    /**
     * 取消订单（支持部分失败）
     * <p>业务流程：</p>
     * <ol>
     *   <li>遍历订单中的每个商品</li>
     *   <li>查询订单明细并验证状态</li>
     *   <li>调用Lua脚本回滚Redis库存</li>
     *   <li>更新数据库中订单状态为已取消</li>
     *   <li>允许部分商品取消失败，返回详细的成功/失败信息</li>
     * </ol>
     * <p>注意：如果Redis回滚成功但数据库更新失败，会产生数据不一致，需要人工介入处理</p>
     *
     * @param orderDTO 订单信息，包含订单号、平台ID和商品列表
     * @return 取消结果，包含每个商品的取消状态详情
     */
    @Override
    public ApiResult<String> cancelOrder(OrderDTO orderDTO) {
        String bizNo = orderDTO.getOrderNo();
        String platformId = orderDTO.getPlatformId();
        // 合并相同商品的取消数量
        Map<Long, Integer> operations = new HashMap<>();
        for (OrderItemDTO item : orderDTO.getItems()) {
            operations.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }

        StructuredLogger.info(ORDER_MYSQL, bizNo, 
                "开始取消订单，platformId={}, 商品种类数={}", platformId, operations.size());
        
        StringBuilder sb = new StringBuilder();
            
        // 参数校验
        if (bizNo == null || bizNo.trim().isEmpty()) {
            return ApiResult.error(400, "订单号不能为空");
        }
        if (platformId == null || platformId.trim().isEmpty()) {
            return ApiResult.error(400, "平台ID不能为空");
        }
        if (operations.isEmpty()) {
            return ApiResult.error(400, "商品信息不能为空");
        }
            
        // 遍历每个商品，逐个取消（允许部分失败）
        for (Map.Entry<Long, Integer> entry : operations.entrySet()) {
            Long productId = entry.getKey();
            Integer cancelQuantity = entry.getValue(); // 前端传入的取消数量
            try {
                // 1. 查询订单明细
                StructuredLogger.debug(ORDER_MYSQL, bizNo, 
                        "查询订单明细，productId={}", productId);
                OrderDetail orderDetail = orderDetailService.findByOrderAndProduct(bizNo, platformId, productId);
                if (orderDetail == null) {
                    sb.append("商品[").append(productId).append("]订单未找到\n");
                    StructuredLogger.warn(ORDER_MYSQL, bizNo, 
                            "取消订单失败，订单明细不存在，productId={}，可能原因：订单未创建或数据已被删除", productId);
                    continue;
                }
                
                // 1.1 验证取消数量（只支持全额取消）
                if (!cancelQuantity.equals(orderDetail.getQuantity())) {
                    sb.append("商品[").append(productId).append("]取消数量不匹配，购买数量=").append(orderDetail.getQuantity()).append(", 取消数量=").append(cancelQuantity).append("\n");
                    StructuredLogger.warn(ORDER_MYSQL, bizNo, 
                            "取消订单失败，取消数量与购买数量不一致，productId={}, 购买数量={}, 取消数量={}，当前业务只支持全额取消", 
                            productId, orderDetail.getQuantity(), cancelQuantity);
                    continue;
                }
                    
                // 2. 检查订单状态（1表示已支付，可以取消）
                if (orderDetail.getStatus() != 1) {
                    sb.append("商品[").append(productId).append("]订单已取消或回滚，当前状态：").append(orderDetail.getStatus()).append("\n");
                    StructuredLogger.info(ORDER_MYSQL, bizNo, 
                            "取消订单跳过，订单状态不符合，productId={}, status={}，可能原因：订单已被取消或回滚", 
                            productId, orderDetail.getStatus());
                    continue;
                }
                    
                // 3. 先执行Lua脚本回滚Redis库存（使用数据库中的完整数量，确保安全）
                Map<Long, Integer> cancelOperations = new HashMap<>();
                cancelOperations.put(orderDetail.getProductId(), orderDetail.getQuantity());
                StructuredLogger.info(ORDER_REDIS, bizNo, 
                        "开始回滚Redis库存，productId={}, quantity={}", productId, orderDetail.getQuantity());
                LuaScriptManager.BatchResult batchResult = luaScriptManager.executeCancel(bizNo, platformId, cancelOperations);
                    
                if (!batchResult.isSuccess()) {
                    sb.append("商品[").append(productId).append("]库存回滚失败：").append(batchResult.getMessage()).append("\n");
                    StructuredLogger.error(ORDER_REDIS, bizNo, 
                            "Redis库存回滚失败，productId={}, 错误信息={}，可能原因：幂等性检查失败或key不存在", 
                            productId, batchResult.getMessage());
                    continue;
                }
                    
                // 4. Redis成功后，更新数据库状态（带重试机制）
                boolean dbUpdated = false;
                int maxRetries = scriptProperties.getRetry().getMaxAttempts();
                long baseInterval = scriptProperties.getRetry().getBaseInterval();
                for (int retry = 0; retry < maxRetries; retry++) {
                    try {
                        StructuredLogger.debug(ORDER_MYSQL, bizNo, 
                                "尝试更新数据库状态，productId={}, 第{}/{}次重试", productId, retry + 1, maxRetries);
                        int result = orderDetailService.updateStatusToCancelByProduct(bizNo, platformId, productId);
                        if (result == 1) {
                            dbUpdated = true;
                            StructuredLogger.info(ORDER_MYSQL, bizNo, 
                                    "数据库状态更新成功，productId={}, 重试次数={}", productId, retry);
                            break;
                        } else {
                            StructuredLogger.warn(ORDER_MYSQL, bizNo, 
                                    "数据库更新返回0，准备重试，productId={}, 第{}/{}次，可能原因：乐观锁冲突或记录不存在", 
                                    productId, retry + 1, maxRetries);
                        }
                    } catch (Exception e) {
                        StructuredLogger.error(ORDER_MYSQL, bizNo, 
                                "数据库更新异常，productId={}, 第{}/{}次重试，可能原因：连接超时或SQL语法错误", 
                                productId, retry + 1, maxRetries, e);
                    }
                        
                    // 重试间隔（指数退避）
                    if (retry < maxRetries - 1) {
                        try {
                            Thread.sleep(baseInterval * (retry + 1));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.warn("重试等待被中断，bizNo={}, platformId={}, productId={}", bizNo, platformId, productId);
                            break;
                        }
                    }
                }
                    
                if (dbUpdated) {
                    // 5. 更新product_stock表的库存（从Redis读取最新值）
                    try {
                        updateProductStockFromRedis(productId);
                        sb.append("商品[").append(productId).append("]取消成功\n");
                        StructuredLogger.info(ORDER_MYSQL, bizNo, 
                                "商品取消流程完成，productId={}", productId);
                    } catch (Exception e) {
                        // product_stock更新失败不影响订单状态，记录警告
                        sb.append("商品[").append(productId).append("]取消成功，但库存备份表更新失败：").append(e.getMessage()).append("\n");
                        StructuredLogger.warn(ORDER_MYSQL, bizNo, 
                                "product_stock更新失败，productId={}，需人工检查库存一致性", productId, e);
                    }
                } else {
                    sb.append("商品[").append(productId).append("]数据库更新失败，Redis已回滚，请联系客服处理\n");
                    // 记录最严重级别的日志，要求人工介入
                    StructuredLogger.error(ORDER_MYSQL, bizNo, 
                            "【严重错误-需人工介入】取消订单时Redis已回滚但数据库更新失败（已重试{}次），productId={}，数据不一致风险！请立即检查并手动修复数据库状态。可能原因：数据库连接永久断开或记录被其他事务锁定。",
                            maxRetries, productId);
                    // 注意：此时不应继续处理其他商品，因为已经出现数据不一致
                    // 但为了尽量完成其他商品的取消，这里选择continue而非break
                }
                    
            } catch (RuntimeException e) {
                // Redis操作彻底失败（重试后仍失败），抛出异常中断整个取消流程
                StructuredLogger.error(ORDER_MYSQL, bizNo, 
                        "取消订单Redis操作彻底失败，productId={}，中断后续商品处理，可能原因：Redis连接永久断开或Lua脚本执行异常", 
                        productId, e);
                throw e; // 重新抛出，让调用方知道有严重错误
            } catch (Exception e) {
                // 捕获所有未预期的异常，确保不影响其他商品的取消
                sb.append("商品[").append(productId).append("]系统异常：").append(e.getMessage()).append("\n");
                StructuredLogger.error(ORDER_MYSQL, bizNo, 
                        "取消订单发生未预期异常，productId={}，可能原因：空指针、类型转换错误或未知业务逻辑错误", 
                        productId, e);
            }
        }
        
        String resultMessage = sb.length() > 0 ? sb.toString() : "没有需要取消的商品";
        StructuredLogger.info(ORDER_MYSQL, bizNo, 
                "订单取消流程结束，结果：{}", resultMessage);
        
        // 检查是否有失败的情况
        if (sb.length() > 0) {
            // 有失败或跳过的商品，返回400状态码
            return ApiResult.error(400, resultMessage);
        }
        
        return ApiResult.success(resultMessage);
    }

    /**
     * 从Redis读取最新库存值并更新到product_stock表
     * @param productId 商品ID
     */
    private void updateProductStockFromRedis(Long productId) {
        String redisKey = "product:stock:" + productId;
        RBucket<Object> bucket = redissonClient.getBucket(redisKey, new StringCodec());
        Object stockValue = bucket.get();
        
        if (stockValue == null) {
            throw new RuntimeException("Redis中不存在该商品库存: " + redisKey);
        }
        
        int currentStock = Integer.parseInt(stockValue.toString());
        
        // 使用乐观锁更新product_stock表
        ProductStock stock = productStockMapper.queryById(productId);
        if (stock == null) {
            // 如果记录不存在，创建新记录
            stock = new ProductStock();
            stock.setProductId(productId);
            stock.setStock(currentStock);
            stock.setVersion(0);
            productStockMapper.insert(stock);
            StructuredLogger.info(ORDER_MYSQL, "SYSTEM", 
                    "商品库存记录不存在，自动创建，productId={}, stock={}", productId, currentStock);
        } else {
            // 使用乐观锁更新
            int maxRetries = 3;
            for (int i = 0; i < maxRetries; i++) {
                int currentVersion = stock.getVersion();
                int rows = productStockMapper.updateStockWithVersion(productId, currentStock, currentVersion);
                if (rows > 0) {
                    StructuredLogger.debug(ORDER_MYSQL, "SYSTEM", 
                            "product_stock更新成功，productId={}, stock={}, version={}", 
                            productId, currentStock, currentVersion);
                    return;
                }
                
                // 乐观锁冲突，重试
                StructuredLogger.warn(ORDER_MYSQL, "SYSTEM", 
                        "乐观锁冲突，重试 {}/{}，productId={}", i + 1, maxRetries, productId);
                if (i < maxRetries - 1) {
                    try {
                        Thread.sleep(10L * (i + 1));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试被中断", e);
                    }
                    // 重新查询最新版本
                    stock = productStockMapper.queryById(productId);
                    if (stock == null) {
                        throw new RuntimeException("库存记录不存在: " + productId);
                    }
                }
            }
            throw new RuntimeException("乐观锁重试失败，productId=" + productId);
        }
    }
}
