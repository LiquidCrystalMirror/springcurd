package org.example.springbootdemo.exception;

/**
 * 服务不可用异常（503）- 熔断/降级触发时抛出
 * <p>与 Resilience4j 熔断器配合使用</p>
 */
public class ServiceUnavailableException extends BusinessException {

    public ServiceUnavailableException(String message) {
        super(503, "SERVICE_UNAVAILABLE", message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(503, "SERVICE_UNAVAILABLE", message, cause);
    }
}
