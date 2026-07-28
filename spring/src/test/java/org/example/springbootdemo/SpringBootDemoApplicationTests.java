package org.example.springbootdemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class SpringBootDemoApplicationTests {

    @Autowired
    private Environment env;

    @Test
    void contextLoads() {
    }

    @Test
    void printEnv() {
        // 从 Spring Environment 读取（.env 注入的目标）
        System.out.println("DB_USERNAME = " + env.getProperty("DB_USERNAME"));
        System.out.println("RABBITMQ_USERNAME = " + env.getProperty("RABBITMQ_USERNAME"));

        // 断言：敏感配置必须存在
        assertNotNull(env.getProperty("DB_USERNAME"), "DB_USERNAME 未加载，请检查 .env 文件");
        assertNotNull(env.getProperty("RABBITMQ_PASSWORD"), "RABBITMQ_PASSWORD 未加载，请检查 .env 文件");
    }
}
