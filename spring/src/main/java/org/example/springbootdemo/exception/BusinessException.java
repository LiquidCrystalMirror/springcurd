package org.example.springbootdemo.exception;

import lombok.Getter;

/**
 * 业务异常基类
 * <p>所有业务异常继承此类，由 GlobalExceptionHandler 统一处理</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** HTTP 状态码 */
    private final int httpStatus;

    /** 业务错误码（如 "STOCK_INSUFFICIENT"） */
    private final String errorCode;

    public BusinessException(int httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public BusinessException(int httpStatus, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }
}
