package org.example.springbootdemo.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResult<T> {
    private Integer code;       // 状态码：200成功，其他失败
    private String message;     // 提示信息
    private T data;             // 成功时返回的数据
    private String errorCode;   // 业务错误码（如 "STOCK_INSUFFICIENT"），仅失败时返回
    private String traceId;     // 链路追踪ID，便于问题排查
    private String timestamp;   // 响应时间戳

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public static <T> ApiResult<T> success(T data) {
        ApiResult<T> apiResult = new ApiResult<>();
        apiResult.setCode(200);
        apiResult.setMessage("success");
        apiResult.setData(data);
        apiResult.setTimestamp(now());
        return apiResult;
    }

    public static <T> ApiResult<T> success() {
        ApiResult<T> apiResult = new ApiResult<>();
        apiResult.setCode(200);
        apiResult.setMessage("success");
        apiResult.setTimestamp(now());
        return apiResult;
    }

    public static <T> ApiResult<T> success(String message) {
        ApiResult<T> apiResult = new ApiResult<>();
        apiResult.setCode(200);
        apiResult.setMessage(message);
        apiResult.setTimestamp(now());
        return apiResult;
    }

    public static <T> ApiResult<T> error(Integer code, String message) {
        ApiResult<T> apiResult = new ApiResult<>();
        apiResult.setCode(code);
        apiResult.setMessage(message);
        apiResult.setTimestamp(now());
        return apiResult;
    }

    public static <T> ApiResult<T> error(Integer code, String errorCode, String message) {
        ApiResult<T> apiResult = new ApiResult<>();
        apiResult.setCode(code);
        apiResult.setErrorCode(errorCode);
        apiResult.setMessage(message);
        apiResult.setTimestamp(now());
        return apiResult;
    }

    private static String now() {
        return LocalDateTime.now().format(FORMATTER);
    }
}
