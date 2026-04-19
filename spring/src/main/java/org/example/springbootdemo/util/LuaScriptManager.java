package org.example.springbootdemo.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.example.springbootdemo.config.StockScriptProperties;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.example.springbootdemo.util.StructuredLogger.LogCategory.*;

/**
 * Lua脚本管理器 - 高度复用设计
 * 负责加载、缓存和执行Redis Lua脚本
 */
@Slf4j
@Component
public class LuaScriptManager {

    @Resource
    private RedissonClient redissonClient;
    @Resource
    private StockScriptProperties scriptProperties;

    // RScript实例（线程安全，可复用）
    private volatile RScript rScript;

    // 脚本SHA缓存：key=脚本路径, value=SHA值
    private final Map<String, String> scriptShaCache = new ConcurrentHashMap<>();

    // 脚本内容缓存：key=脚本路径, value=脚本内容
    private final Map<String, String> scriptContentCache = new ConcurrentHashMap<>();

    // JSON解析器（线程安全）
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 初始化RScript实例
     */
    @PostConstruct
    public void init() {
        this.rScript = redissonClient.getScript();
        log.info("LuaScriptManager initialized");
    }

    /**
     * 加载脚本内容（带缓存）
     * @param scriptPath 脚本在classpath中的路径
     * @return 脚本内容字符串
     */
    public String loadScript(String scriptPath) {
        return scriptContentCache.computeIfAbsent(scriptPath, path -> {
            try {
                ClassPathResource resource = new ClassPathResource(path);
                String content = StreamUtils.copyToString(
                        resource.getInputStream(),
                        StandardCharsets.UTF_8
                );
                log.debug("Loaded script: {}", path);
                return content;
            } catch (Exception e) {
                throw new RuntimeException("Failed to load script: " + path, e);
            }
        });
    }

    /**
     * 预加载脚本到Redis（计算SHA值）
     * @param scriptPath 脚本路径
     * @return 脚本的SHA值
     */
    public String preloadScript(String scriptPath) {
        return scriptShaCache.computeIfAbsent(scriptPath, path -> {
            String content = loadScript(path);
            String sha = rScript.scriptLoad(content);
            log.info("Preloaded script: {} -> SHA: {}", path, sha);
            return sha;
        });
    }

    /**
     * 通用脚本执行方法（自动重试机制）
     * 执行流程：先尝试用SHA执行，失败后回退到直接执行脚本内容
     * 注意：使用 StringCodec 避免 JSON 解析错误
     * 
     * @param scriptPath 脚本路径
     * @param returnType 返回值类型
     * @param keys Redis keys列表
     * @param args 脚本参数
     * @return 脚本执行结果
     */
    //@SuppressWarnings("unchecked")
    public <T> T execute(String scriptPath,
                         RScript.ReturnType returnType,
                         List<Object> keys,
                         Object... args) {
        String sha = preloadScript(scriptPath);
        // 使用 StringCodec 执行 Lua 脚本，避免 JsonJacksonCodec 解析非 JSON 数据时报错
        RScript scriptWithStringCodec = redissonClient.getScript(StringCodec.INSTANCE);

        // 最多重试3次
        for (int i = 0; i < 3; i++) {
            try {
                // 使用SHA执行脚本（高效，减少网络传输）
                return scriptWithStringCodec.evalSha(
                        RScript.Mode.READ_WRITE,
                        sha,
                        returnType,
                        keys,
                        args
                );
            } catch (Exception e) {
                log.warn("Script execution failed with SHA (attempt {}/3): {}", i + 1, e.getMessage());
                
                if (i == 2) {
                    // 最后一次失败，回退到直接执行脚本内容
                    log.info("Falling back to direct script execution");
                    String content = loadScript(scriptPath);
                    // 重新加载SHA缓存
                    scriptShaCache.remove(scriptPath);
                    preloadScript(scriptPath);
                    
                    return scriptWithStringCodec.eval(
                            RScript.Mode.READ_WRITE,
                            content,
                            returnType,
                            keys,
                            args
                    );
                }
                
                // 指数退避重试
                try {
                    Thread.sleep(100 * (i + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
            }
        }
        throw new RuntimeException("Script execution failed after retries");
    }

    /**
     * 批量扣减/增加执行器
     * @param bizNo 业务单号
     * @param operations 操作映射：key=Redis键名, value=操作数量
     * @param opType 操作类型：deduct=扣减, add=增加
     * @return 批量执行结果
     */
    public BatchResult executeBatchOperation(String bizNo,
                                             String platformId,
                                             Map<Long, Integer> operations,
                                             String opType) {
        // 准备KEYS数组（将商品ID转换为Redis key格式）
        List<Object> keys = new ArrayList<>();
        for (Long productId : operations.keySet()) {
            keys.add("product:stock:" + productId);
        }

        // 准备ARGV参数
        List<Object> args = new ArrayList<>();
        // 添加每个key对应的操作值
        operations.values().forEach(args::add);
        // 添加业务单号
        args.add(bizNo);
        // 添加平台ID
        args.add(platformId);
        // 从配置中获取超时时间
        args.add(String.valueOf(scriptProperties.getTimeout()));
        // 添加操作类型
        args.add(opType);

        // 从配置中获取脚本路径
        String scriptPath = scriptProperties.getPath().getBatchOperation();
        // 执行脚本
        List<Object> result = execute(
                scriptPath,
                RScript.ReturnType.MULTI,
                keys,
                args.toArray()
        );

        return parseBatchResult(result, bizNo);
    }

    /**
     * 批量扣减执行器（向后兼容）
     */
    public BatchResult executeBatchDeduct(String bizNo, String platformId, Map<Long, Integer> operations) {
        return executeBatchOperation(bizNo, platformId, operations, scriptProperties.getType().getDeduct());
    }
    /**
     * 批量增加执行器（向后兼容）
     */
    public BatchResult executeBatchAdd(String bizNo, String platformId, Map<Long, Integer> operations) {
        return executeBatchOperation(bizNo, platformId, operations, scriptProperties.getType().getAdd());
    }
    /**
     * 批量回滚执行器
     * @param bizNo 业务单号
     * @param opType 操作类型
     * @param keys 需要回滚的key列表（null表示恢复所有）
     * @param deleteSnapshot 是否删除快照
     * @return 批量执行结果
     */
    public BatchResult executeRollback(String bizNo,
                                       String platformId,
                                       String opType,
                                       List<String> keys,
                                       boolean deleteSnapshot) {
        List<Object> args = new ArrayList<>();
        args.add(bizNo);
        args.add(platformId);
        args.add(opType);
        args.add(deleteSnapshot ? "1" : "0");

        List<Object> keysObj = keys == null ? new ArrayList<>() : new ArrayList<>(keys);

        // 从配置中获取脚本路径
        String scriptPath = scriptProperties.getPath().getRollback();
        List<Object> result = execute(
                scriptPath,
                RScript.ReturnType.MULTI,
                keysObj,
                args.toArray()
        );

        return parseBatchResult(result, bizNo);
    }

    /**
     * 批量查询执行器
     * @param keys 要查询的key列表
     * @param bizNo 业务单号（用于日志）
     * @return 查询结果：key=Redis键名, value=值
     */
    public Map<String, String> executeBatchQuery(List<String> keys, String bizNo) {
        String scriptPath = scriptProperties.getPath().getQuery();
        List<Object> result = execute(
                scriptPath,
                RScript.ReturnType.MULTI,
                new ArrayList<>(keys),
                bizNo
        );

        // 检查结果：result = [1, "success", json_data]
        if (result != null && result.size() >= 3 && "success".equals(String.valueOf(result.get(1)))) {
            String jsonData = String.valueOf(result.get(2));
            return parseQueryResult(jsonData);
        }
        return Collections.emptyMap();
    }

    /**
     * 取消订单库存恢复执行器
     * 直接增加 Redis 库存，不保存快照，不发队列
     * 注意：这是补偿操作，必须保证成功，已添加重试机制
     *
     * @param bizNo      订单号
     * @param platformId 平台标识
     * @param operations 商品 key 与恢复数量的映射
     * @return 执行结果
     */
    public BatchResult executeCancel(String bizNo,
                                     String platformId,
                                     Map<Long, Integer> operations) {
        int maxRetries = 3;
        Exception lastException = null;
        
        for (int i = 0; i < maxRetries; i++) {
            try {
                // 准备 KEYS 数组（将商品ID转换为Redis key格式）
                List<Object> keys = new ArrayList<>();
                for (Long productId : operations.keySet()) {
                    keys.add("product:stock:" + productId);
                }

                // 准备 ARGV 参数
                List<Object> args = new ArrayList<>();
                operations.values().forEach(args::add);          // 每个 key 的增加数量
                args.add(bizNo);                                 // 业务单号
                args.add(platformId);                            // 平台 ID
                args.add(String.valueOf(scriptProperties.getTimeout())); // 超时时间

                // 获取取消脚本路径
                String scriptPath = scriptProperties.getPath().getCancel();
                List<Object> result = execute(
                        scriptPath,
                        RScript.ReturnType.MULTI,
                        keys,
                        args.toArray()
                );

                BatchResult batchResult = parseBatchResult(result, bizNo);
                
                // 检查是否成功
                if (batchResult.isSuccess()) {
                    StructuredLogger.info(ORDER_REDIS, bizNo, 
                            "取消订单库存恢复成功");
                    return batchResult;
                }
                
                // 记录失败日志并重试
                StructuredLogger.warn(ORDER_REDIS, bizNo, 
                        "取消订单库存恢复失败，第{}次重试，message={}", 
                        i + 1, batchResult.getMessage());
                         
            } catch (Exception e) {
                lastException = e;
                StructuredLogger.error(ORDER_REDIS, bizNo, 
                        "取消订单Redis操作异常，第{}次重试，可能原因：Redis连接断开、Lua脚本错误或网络超时", 
                        i + 1, e);
            }
            
            // 指数退避重试
            if (i < maxRetries - 1) {
                try {
                    Thread.sleep(100 * (i + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("取消订单被中断", ie);
                }
            }
        }
        
        // 所有重试都失败，抛出异常让调用方处理
        throw new RuntimeException(
            String.format("取消订单Redis恢复失败（已重试%d次），bizNo=%s，需人工介入", 
                          maxRetries, bizNo), 
            lastException
        );
    }

    /**
     * 解析批量操作结果
     * @param result 脚本返回结果
     * @param bizNo 业务单号
     * @return 封装后的BatchResult对象
     */
    private BatchResult parseBatchResult(List<Object> result, String bizNo) {
        if (result == null || result.size() < 3) {
            return BatchResult.fail("INVALID_RESULT", "脚本返回结果异常", bizNo);
        }

        int status = Integer.parseInt(String.valueOf(result.get(0)));
        String message = String.valueOf(result.get(1));

        if (status == 1) {
            // 成功：result = [1, "success", bizNo, snapshotKey]
            String snapshotKey = result.size() > 3 ? String.valueOf(result.get(3)) : null;
            return BatchResult.success(bizNo, message, snapshotKey);
        } else {
            // 失败：result = [0, error_message, detail]
            String detail = result.size() > 2 ? String.valueOf(result.get(2)) : null;
            return BatchResult.fail(message, detail, bizNo);
        }
    }

    /**
     * 解析查询结果JSON
     * @param jsonData JSON格式的查询结果
     * @return Map形式的查询结果
     */
    private Map<String, String> parseQueryResult(String jsonData) {
        try {
            // 使用Jackson解析JSON字符串
            return objectMapper.readValue(jsonData, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.error("Failed to parse query result JSON: {}", jsonData, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 批量执行结果内部类
     */
    @Getter
    public static class BatchResult {
        // Getters
        private boolean success;
        private String code;
        private String message;
        private String bizNo;
        private String snapshotKey;
        private String detail;

        public static BatchResult success(String bizNo, String message, String snapshotKey) {
            BatchResult result = new BatchResult();
            result.success = true;
            result.code = "SUCCESS";
            result.message = message;
            result.bizNo = bizNo;
            result.snapshotKey = snapshotKey;
            return result;
        }

        public static BatchResult fail(String code, String message, String bizNo) {
            BatchResult result = new BatchResult();
            result.success = false;
            result.code = code;
            result.message = message;
            result.bizNo = bizNo;
            return result;
        }

        public static BatchResult fail(String code, String message, String detail, String bizNo) {
            BatchResult result = fail(code, message, bizNo);
            result.detail = detail;
            return result;
        }

    }
    /**
     * 获取错误信息
     * @param result 批量操作结果
     * @return 错误信息
     */
    public static String getErrorMessage(BatchResult result) {
        if (result.isSuccess()) {
            return null;
        }
        String code = result.getCode();
        return switch (code) {
            case "no_keys_provided" -> "未指定操作的商品";
            case "already_failed" -> "该操作历史执行失败，无法继续";
            case "key_not_found" -> "商品库存数据异常";
            case "insufficient_value" -> "库存不足";
            case "negative_stock_detected" -> "库存扣减异常";
            case "unsupported_operation" -> "不支持的操作类型";
            case "snapshot_not_found" -> "回滚快照不存在";
            case "INVALID_RESULT" -> "Redis脚本返回异常";
            default -> "操作失败：" + result.getMessage();
        };
    }




    /**
     * 补货执行结果
     */
    @Getter
    public static class ReplenishResult {
        private final boolean success;
        private final String code;
        private final String message;
        private final String replenishNo;
        private final Map<Long, StockChange> changes;

        public ReplenishResult(boolean success, String code, String message,
                               String replenishNo, Map<Long, StockChange> changes) {
            this.success = success;
            this.code = code;
            this.message = message;
            this.replenishNo = replenishNo;
            this.changes = changes;
        }

        @Getter
        @AllArgsConstructor
        public static class StockChange {
            private int before;
            private int after;
        }
    }

    /**
     * 执行补货（同步，无队列）
     */
    public ReplenishResult executeReplenish(String replenishNo, Map<Long, Integer> operations) {
        List<Object> keys = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : operations.entrySet()) {
            keys.add("product:stock:" + e.getKey());
            args.add(e.getValue());  // 直接传递 Integer，让 Redisson 处理
        }
        args.add(replenishNo);
        args.add(scriptProperties.getTimeout());  // 直接传递 int，不要转字符串

        String scriptPath = scriptProperties.getPath().getReplenish();
        List<Object> result = execute(scriptPath, RScript.ReturnType.MULTI, keys, args.toArray());
        return parseReplenishResult(result);
    }

    private ReplenishResult parseReplenishResult(List<Object> result) {
        if (result == null || result.size() < 3) {
            return new ReplenishResult(false, "INVALID_RESULT", "脚本返回异常", null, null);
        }
        int status = Integer.parseInt(String.valueOf(result.get(0)));
        String message = String.valueOf(result.get(1));
        String replenishNo = result.size() > 2 ? String.valueOf(result.get(2)) : null;

        if (status == 1) {
            Map<Long, ReplenishResult.StockChange> changes = new HashMap<>();
            if (result.size() > 3 && !"{}".equals(String.valueOf(result.get(3)))) {
                try {
                    Map<String, Map<String, Integer>> raw = objectMapper.readValue(
                            String.valueOf(result.get(3)),
                            new TypeReference<Map<String, Map<String, Integer>>>() {});
                    for (Map.Entry<String, Map<String, Integer>> e : raw.entrySet()) {
                        Long pid = Long.parseLong(e.getKey().substring(e.getKey().lastIndexOf(':') + 1));
                        changes.put(pid, new ReplenishResult.StockChange(
                                e.getValue().get("before"), e.getValue().get("after")));
                    }
                } catch (Exception e) {
                    StructuredLogger.error(REPLENISH_REDIS, replenishNo != null ? replenishNo : "UNKNOWN", 
                            "解析补货结果失败，可能原因：JSON格式不正确或数据结构异常", e);
                }
            }
            return new ReplenishResult(true, "SUCCESS", message, replenishNo, changes);
        } else {
            return new ReplenishResult(false, message, message, replenishNo, null);
        }
    }
}