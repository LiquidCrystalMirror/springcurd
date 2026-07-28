package org.example.springbootdemo.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.example.springbootdemo.util.ApiResult;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器（重构版）
 * <p>分层处理：业务异常 → 参数校验 → 数据库异常 → 未知异常</p>
 * <p>关键改进：移除 e.printStackTrace()，使用规范日志；引入业务异常体系替代字符串匹配</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 业务异常（精确匹配） ====================

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResult<Void> handleUnauthorized(UnauthorizedException e) {
        log.warn("[认证失败] errorCode={}, message={}", e.getErrorCode(), e.getMessage());
        return buildError(401, e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResult<Void> handleForbidden(ForbiddenException e) {
        log.warn("[权限不足] errorCode={}, message={}", e.getErrorCode(), e.getMessage());
        return buildError(403, e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResult<Void> handleNotFound(NotFoundException e) {
        log.warn("[资源不存在] errorCode={}, message={}", e.getErrorCode(), e.getMessage());
        return buildError(404, e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleBadRequest(BadRequestException e) {
        log.warn("[请求参数错误] errorCode={}, message={}", e.getErrorCode(), e.getMessage());
        return buildError(400, e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResult<Void> handleConflict(ConflictException e) {
        log.warn("[业务冲突] errorCode={}, message={}", e.getErrorCode(), e.getMessage());
        return buildError(409, e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResult<Void> handleServiceUnavailable(ServiceUnavailableException e) {
        log.error("[服务熔断/降级] errorCode={}, message={}", e.getErrorCode(), e.getMessage());
        return buildError(503, e.getErrorCode(), e.getMessage());
    }

    // ==================== 参数校验（框架层） ====================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[参数校验失败] {}", detail);
        return buildError(400, "VALIDATION_FAILED", detail);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String msg = "参数 " + e.getName() + " 类型不正确，期望 " +
                (e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知类型");
        log.warn("[参数类型不匹配] {}", msg);
        return buildError(400, "TYPE_MISMATCH", msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("[缺少参数] {}", e.getParameterName());
        return buildError(400, "MISSING_PARAM", "缺少必要参数: " + e.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("[JSON解析失败] {}", e.getMessage());
        return buildError(400, "JSON_PARSE_ERROR", "请求参数格式错误，请检查JSON格式");
    }

    // ==================== HTTP 层异常 ====================

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResult<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("[不支持的HTTP方法] {}", e.getMessage());
        return buildError(405, "METHOD_NOT_ALLOWED", "不支持的请求方法: " + e.getMethod());
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResult<Void> handleNoHandler(Exception e) {
        log.warn("[接口不存在] {}", e.getMessage());
        return buildError(404, "NOT_FOUND", "请求的接口不存在");
    }

    // ==================== 数据库层异常 ====================

    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleDuplicateKey(DuplicateKeyException e) {
        String message = e.getMessage();
        if (message != null && message.contains("PRIMARY")) {
            log.warn("[主键冲突] ID已存在");
            return buildError(400, "DUPLICATE_KEY", "ID已存在");
        }
        if (message != null && message.contains("name")) {
            log.warn("[唯一约束冲突] 名称已存在");
            return buildError(400, "DUPLICATE_KEY", "名称已存在");
        }
        log.warn("[唯一约束冲突] {}", message);
        return buildError(400, "DUPLICATE_KEY", "数据已存在");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        String message = e.getMessage();
        if (message != null && message.contains("NOT NULL")) {
            log.warn("[数据完整性] 缺少必要参数");
            return buildError(400, "DATA_INTEGRITY", "缺少必要参数");
        }
        log.warn("[数据完整性] {}", message);
        return buildError(400, "DATA_INTEGRITY", "数据完整性错误");
    }

    // ==================== 兜底异常 ====================

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<Void> handleRuntime(RuntimeException e) {
        log.error("[运行时异常] type={}, message={}", e.getClass().getSimpleName(), e.getMessage(), e);
        return buildError(500, "INTERNAL_ERROR", "服务器内部错误: " + e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<Void> handleAll(Exception e) {
        log.error("[系统未知异常] type={}, message={}", e.getClass().getSimpleName(), e.getMessage(), e);
        return buildError(500, "INTERNAL_ERROR", "服务器内部错误，请稍后重试");
    }

    // ==================== 辅助方法 ====================

    private ApiResult<Void> buildError(int code, String errorCode, String message) {
        ApiResult<Void> result = new ApiResult<>();
        result.setCode(code);
        result.setMessage(message);
        result.setErrorCode(errorCode);
        result.setTimestamp(java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
        // 从 MDC 取 TraceId（如果有）
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            result.setTraceId(traceId);
        }
        return result;
    }
}
