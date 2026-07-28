package org.example.springbootdemo.exception;

/**
 * 资源不存在异常（404）
 */
public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super(404, "NOT_FOUND", message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(404, "NOT_FOUND", message, cause);
    }
}
