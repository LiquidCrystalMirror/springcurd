package org.example.springbootdemo.util;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 结构化日志工具类
 * 按照业务模块和数据库类型分类记录日志，便于问题追踪和Debug
 * 
 * 日志分类结构：
 * - order.redis.create    : 订单创建-Redis操作
 * - order.redis.cancel    : 订单取消-Redis操作
 * - order.mysql.create    : 订单创建-MySQL操作
 * - order.mysql.cancel    : 订单取消-MySQL操作
 * - replenish.redis       : 补货-Redis操作
 * - replenish.mysql       : 补货-MySQL操作
 */
@Slf4j
public class StructuredLogger {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * 日志分类枚举
     * 采用2层命名结构：模块.操作类型
     * 例如：order.redis, order.mysql, replenish.redis, replenish.mysql
     */
    public enum LogCategory {
        // 订单相关
        ORDER_REDIS("order.redis"),
        ORDER_MYSQL("order.mysql"),
        
        // 补货相关
        REPLENISH_REDIS("replenish.redis"),
        REPLENISH_MYSQL("replenish.mysql");

        private final String category;

        LogCategory(String category) {
            this.category = category;
        }

        public String getCategory() {
            return category;
        }
    }

    /**
     * 记录INFO级别日志
     *
     * @param category 日志分类
     * @param bizNo    业务单号（订单号/补货单号）
     * @param message  日志消息
     * @param args     参数
     */
    public static void info(LogCategory category, String bizNo, String message, Object... args) {
        setMDC(category, bizNo);
        try {
            log.info("[{}] " + message, prependArgs(args));
        } finally {
            clearMDC();
        }
    }

    /**
     * 记录WARN级别日志
     *
     * @param category 日志分类
     * @param bizNo    业务单号
     * @param message  日志消息
     * @param args     参数
     */
    public static void warn(LogCategory category, String bizNo, String message, Object... args) {
        setMDC(category, bizNo);
        try {
            log.warn("[{}] " + message, prependArgs(args));
        } finally {
            clearMDC();
        }
    }

    /**
     * 记录ERROR级别日志（带异常）
     *
     * @param category 日志分类
     * @param bizNo    业务单号
     * @param message  日志消息
     * @param e        异常对象
     * @param args     参数
     */
    public static void error(LogCategory category, String bizNo, String message, Throwable e, Object... args) {
        setMDC(category, bizNo);
        try {
            log.error("[{}] " + message, prependArgs(args), e);
        } finally {
            clearMDC();
        }
    }

    /**
     * 记录ERROR级别日志（不带异常）
     *
     * @param category 日志分类
     * @param bizNo    业务单号
     * @param message  日志消息
     * @param args     参数
     */
    public static void error(LogCategory category, String bizNo, String message, Object... args) {
        setMDC(category, bizNo);
        try {
            log.error("[{}] " + message, prependArgs(args));
        } finally {
            clearMDC();
        }
    }

    /**
     * 记录DEBUG级别日志
     *
     * @param category 日志分类
     * @param bizNo    业务单号
     * @param message  日志消息
     * @param args     参数
     */
    public static void debug(LogCategory category, String bizNo, String message, Object... args) {
        setMDC(category, bizNo);
        try {
            log.debug("[{}] " + message, prependArgs(args));
        } finally {
            clearMDC();
        }
    }

    /**
     * 设置MDC上下文信息
     */
    private static void setMDC(LogCategory category, String bizNo) {
        MDC.put("logCategory", category.getCategory());
        MDC.put("bizNo", bizNo != null ? bizNo : "N/A");
        MDC.put("timestamp", LocalDateTime.now().format(FORMATTER));
    }

    /**
     * 清除MDC上下文
     */
    private static void clearMDC() {
        MDC.clear();
    }

    /**
     * 在参数数组前添加时间戳
     */
    private static Object[] prependArgs(Object... args) {
        if (args == null || args.length == 0) {
            return new Object[]{LocalDateTime.now().format(FORMATTER)};
        }
        
        Object[] newArgs = new Object[args.length + 1];
        newArgs[0] = LocalDateTime.now().format(FORMATTER);
        System.arraycopy(args, 0, newArgs, 1, args.length);
        return newArgs;
    }
}
