package org.example.springbootdemo.exception;

/**
 * 参数校验异常（400）
 */
public class BadRequestException extends BusinessException {

    public BadRequestException(String message) {
        super(400, "BAD_REQUEST", message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(400, "BAD_REQUEST", message, cause);
    }
}
