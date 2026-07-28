package org.example.springbootdemo.exception;

/**
 * 业务冲突异常（409）- 如幂等性拦截、数据已被他人修改
 */
public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(409, "CONFLICT", message);
    }

    public ConflictException(String message, Throwable cause) {
        super(409, "CONFLICT", message, cause);
    }
}
