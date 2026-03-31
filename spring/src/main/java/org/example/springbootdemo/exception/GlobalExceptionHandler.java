package org.example.springbootdemo.exception;

import org.example.springbootdemo.dto.ApiResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理主键冲突、唯一约束冲突（id重复、name重复）
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ApiResult<Void> handleDuplicateKeyException(DuplicateKeyException e) {
        // 判断是哪个字段冲突
        String message = e.getMessage();
        if (message.contains("PRIMARY")) {
            return ApiResult.error(400, "用户ID已存在");
        } else if (message.contains("name")) {
            return ApiResult.error(400, "用户名已存在");
        }
        return ApiResult.error(400, "数据已存在");
    }

    /**
     * 处理数据库完整性约束异常（NOT NULL 等）
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ApiResult<Void> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        String message = e.getMessage();
        if (message.contains("NOT NULL")) {
            return ApiResult.error(400, "缺少必要参数");
        }
        return ApiResult.error(400, "数据完整性错误");
    }

    /**
     * 处理 JSON 解析错误（缺少逗号等格式错误）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResult<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        return ApiResult.error(400, "请求参数格式错误，请检查JSON格式");
    }

    /**
     * 处理参数类型不匹配（如 String 传给 Integer）
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResult<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        return ApiResult.error(400, "参数类型错误：" + e.getName() + " 类型不正确");
    }

    /**
     * 处理缺少参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResult<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        return ApiResult.error(400, "缺少必要参数：" + e.getParameterName());
    }

    /**
     * 处理参数验证失败（配合 @Valid 使用）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ApiResult.error(400, message);
    }

    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleException(Exception e) {
        e.printStackTrace(); // 打印日志方便调试
        return ApiResult.error(500, "服务器内部错误：" + e.getMessage());
    }
}