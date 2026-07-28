package org.example.springbootdemo.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * 在 Spring Environment 准备阶段（ConfigData 解析之前）将 .env 注入，
 * 确保 spring.config.import 导入的文件也能解析 ${...} 占位符。
 * <p>
 * 使用 @Order(HIGHEST_PRECEDENCE) 确保在 ConfigDataEnvironmentPostProcessor
 * （优先级 HIGHEST_PRECEDENCE + 10）之前执行。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String loadedFrom = ".";
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
        if (dotenv.entries().isEmpty()) {
            loadedFrom = "..";
            dotenv = Dotenv.configure()
                    .directory("..")
                    .ignoreIfMissing()
                    .load();
        }
        if (dotenv.entries().isEmpty()) {
            System.err.println("[警告] 未找到 .env 文件，敏感配置将无法加载！");
        } else {
            Map<String, Object> envMap = new HashMap<>();
            dotenv.entries().forEach(e -> envMap.put(e.getKey(), e.getValue()));
            environment.getPropertySources()
                    .addFirst(new MapPropertySource("dotenv", envMap));
            System.out.println("[INFO] 从目录 [" + loadedFrom + "] 加载 .env，共 " + envMap.size() + " 项");
        }
    }
}
