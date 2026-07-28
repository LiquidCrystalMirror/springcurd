package org.example.springbootdemo.exception;

/**
 * 认证异常（401）- 未登录或Token无效
 */
public class UnauthorizedException extends BusinessException {

    public UnauthorizedException(String message) {
        super(401, "UNAUTHORIZED", message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(401, "UNAUTHORIZED", message, cause);
    }
}
