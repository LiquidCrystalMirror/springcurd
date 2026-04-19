package org.example.springbootdemo.dto;

import lombok.Data;

@Data
public class ApiResult<T> {
    private Integer code;    // 状态码：200成功，其他失败
    private String message;  // 提示信息
    private T data;          // 成功时返回的数据

    public static <T> ApiResult<T> success(T data) {
        ApiResult<T> apiResult = new ApiResult<>();
        apiResult.setCode(200);
        apiResult.setMessage("success");
        apiResult.setData(data);
        return apiResult;
    }

    public static <T> ApiResult<T> success() {
        ApiResult<T> apiResult = new ApiResult<>();
        apiResult.setCode(200);
        apiResult.setMessage("success");
        return apiResult;
    }

    public static <T> ApiResult<T> success(String message) {
        ApiResult<T> apiResult = new ApiResult<>();
        apiResult.setCode(200);
        apiResult.setMessage(message);
        return apiResult;
    }

    public static <T> ApiResult<T> error(Integer code, String message) {
        ApiResult<T> apiResult = new ApiResult<>();
        apiResult.setCode(code);
        apiResult.setMessage(message);
        return apiResult;
    }

}