package org.example.springbootdemo.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Configuration
public class RedissonConfig {

    /** 独立 Redis 连接地址（优先于 YAML 块配置，支持 .env 覆盖） */
    @Value("${redis.address:}")
    private String redisAddress;

    /** 独立 Redis 数据库编号 */
    @Value("${redis.database:0}")
    private int redisDatabase;

    /** 独立 Redis 连接池大小 */
    @Value("${redis.pool-size:64}")
    private int redisPoolSize;

    /** 完整 Redisson YAML 块配置（兜底方案） */
    @Value("${redis.redisson.config:}")
    private String redissonYamlConfig;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() throws IOException {
        Config config;

        // 优先使用独立属性构建配置（支持 .env 中的环境变量）
        if (StringUtils.hasText(redisAddress)) {
            config = new Config();
            config.useSingleServer()
                    .setAddress(redisAddress)
                    .setDatabase(redisDatabase)
                    .setConnectionPoolSize(redisPoolSize);
            config.setCodec(new JsonJacksonCodec());
        } else if (StringUtils.hasText(redissonYamlConfig)) {
            // 回退：从 YAML 字符串加载配置
            config = Config.fromYAML(redissonYamlConfig);
        } else {
            // 最终兜底：默认单机配置
            config = new Config();
            config.useSingleServer()
                    .setAddress("redis://127.0.0.1:6379")
                    .setDatabase(0)
                    .setConnectionPoolSize(64);
            config.setCodec(new JsonJacksonCodec());
        }
        return Redisson.create(config);
    }
}