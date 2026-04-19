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

    @Value("${redis.redisson.config:}")
    private String redissonYamlConfig;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() throws IOException {
        Config config;
        if (StringUtils.hasText(redissonYamlConfig)) {
            // 从 YAML 字符串加载配置
            config = Config.fromYAML(redissonYamlConfig);
        } else {
            // 默认单机配置（兜底）
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