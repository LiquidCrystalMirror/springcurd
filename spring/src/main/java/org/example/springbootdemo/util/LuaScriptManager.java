package org.example.springbootdemo.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.example.springbootdemo.config.StockScriptProperties;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

        // 最多重试3次
        for (int i = 0; i < 3; i++) {
            try {
                // 使用SHA执行脚本（高效，减少网络传输）
                return rScript.evalSha(
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
                    
                    return rScript.eval(
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
                                             Map<String, Integer> operations,
                                             String opType) {
        // 准备KEYS数组
        List<Object> keys = new ArrayList<>(operations.keySet());

        // 准备ARGV参数
        List<Object> args = new ArrayList<>();
        // 添加每个key对应的操作值
        operations.values().forEach(args::add);
        // 添加业务单号
        args.add(bizNo);
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
    public BatchResult executeBatchDeduct(String bizNo, Map<String, Integer> operations) {
        return executeBatchOperation(bizNo, operations, scriptProperties.getType().getDeduct());
    }
    /**
     * 批量增加执行器（向后兼容）
     */
    public BatchResult executeBatchAdd(String bizNo, Map<String, Integer> operations) {
        return executeBatchOperation(bizNo, operations, scriptProperties.getType().getAdd());
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
                                       String opType,
                                       List<String> keys,
                                       boolean deleteSnapshot) {
        List<Object> args = new ArrayList<>();
        args.add(bizNo);
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
}