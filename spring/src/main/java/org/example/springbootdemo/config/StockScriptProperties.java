package org.example.springbootdemo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 库存脚本配置属性类
 * 对应 application-redis.yml 中的 stock.script 配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "stock.script")
public class StockScriptProperties {

    /**
     * 脚本执行默认超时时间（秒）
     */
    private int timeout = 300;

    /**
     * 脚本路径配置
     */
    private PathConfig path = new PathConfig();

    /**
     * 操作类型配置
     */
    private TypeConfig type = new TypeConfig();

    /**
     * 重试配置
     */
    private RetryConfig retry = new RetryConfig();

    @Data
    public static class PathConfig {
        private String batchOperation = "lua/batch_deduct.lua";
        private String rollback = "lua/rollback.lua";
        private String query = "lua/query.lua";
        private String cancel = "lua/cancel.lua";
        private String replenish = "lua/replenish.lua";
    }

    @Data
    public static class TypeConfig {
        private String deduct = "deduct";
        private String add = "add";
        private String rollback = "rollback";
    }

    @Data
    public static class RetryConfig {
        private int maxAttempts = 3;  // 最大重试次数
        private long baseInterval = 100;  // 基础重试间隔（毫秒）
    }
}