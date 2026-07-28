package org.example.springbootdemo.exception;

/**
 * 权限不足异常（403）- 角色权限不够
 */
public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super(403, "FORBIDDEN", message);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(403, "FORBIDDEN", message, cause);
    }
}
