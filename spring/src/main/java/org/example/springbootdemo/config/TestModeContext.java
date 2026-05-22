package org.example.springbootdemo.config;

/**
 * 测试模式上下文 - 用于标识当前请求是否为测试请求
 */
public class TestModeContext {
    private static final ThreadLocal<Boolean> testMode = new ThreadLocal<>();

    /**
     * 设置测试模式
     */
    public static void setTestMode(boolean isTest) {
        testMode.set(isTest);
    }

    /**
     * 获取测试模式状态
     */
    public static boolean isTestMode() {
        return Boolean.TRUE.equals(testMode.get());
    }

    /**
     * 清除测试模式
     */
    public static void clear() {
        testMode.remove();
    }
}
