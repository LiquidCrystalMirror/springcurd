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

    @Data
    public static class PathConfig {
        private String batchOperation = "lua/batch_deduct.lua";
        private String rollback = "lua/rollback.lua";
        private String query = "lua/query.lua";
    }

    @Data
    public static class TypeConfig {
        private String deduct = "deduct";
        private String add = "add";
    }
}