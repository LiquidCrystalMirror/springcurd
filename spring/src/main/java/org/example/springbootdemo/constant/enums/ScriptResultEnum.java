package org.example.springbootdemo.constant.enums;

import lombok.Getter;
import org.example.springbootdemo.dto.ApiResult;
import org.example.springbootdemo.util.LuaScriptManager;

/**
 * Lua脚本执行结果枚举
 * 对应 BatchResult 中的 code/message 组合
 */
@Getter
public enum ScriptResultEnum {

    // ========== 成功状态 ==========
    SUCCESS("SUCCESS", "success", true, 200, "操作成功"),
    ALREADY_SUCCESS("SUCCESS", "already_success", true, 200, "操作已成功处理，请勿重复提交"),
    ROLLBACK_SUCCESS("SUCCESS", "rollback_success", true, 200, "回滚成功"),

    // ========== 失败状态 ==========
    NO_KEYS_PROVIDED("no_keys_provided", "no_keys_provided", false, 400, "未指定操作的商品"),
    ALREADY_FAILED("already_failed", "already_failed", false, 500, "该操作历史执行失败，无法继续"),
    KEY_NOT_FOUND("key_not_found", "key_not_found", false, 500, "商品库存数据不存在"),
    INSUFFICIENT_VALUE("insufficient_value", "insufficient_value", false, 400, "库存不足"),
    NEGATIVE_STOCK_DETECTED("negative_stock_detected", "negative_stock_detected", false, 500, "库存扣减异常（负数）"),
    UNSUPPORTED_OPERATION("unsupported_operation", "unsupported_operation", false, 400, "不支持的操作类型"),
    SNAPSHOT_NOT_FOUND("snapshot_not_found", "snapshot_not_found", false, 400, "回滚快照不存在，无法回滚"),
    INVALID_RESULT("INVALID_RESULT", "INVALID_RESULT", false, 500, "Redis脚本返回异常"),

    UNKNOWN("UNKNOWN", "UNKNOWN", false, 500, "未知错误");

    private final String code;          // 对应 BatchResult.code
    private final String message;       // 对应 BatchResult.message
    private final boolean success;      // 是否成功
    private final int httpStatus;       // 映射到 ApiResult 的 code
    private final String defaultDesc;   // 默认描述

    ScriptResultEnum(String code, String message, boolean success, int httpStatus, String defaultDesc) {
        this.code = code;
        this.message = message;
        this.success = success;
        this.httpStatus = httpStatus;
        this.defaultDesc = defaultDesc;
    }

    /**
     * 根据 BatchResult 的 code 和 message 精确匹配枚举
     */
    public static ScriptResultEnum fromBatchResult(LuaScriptManager.BatchResult result) {
        if (result == null) return UNKNOWN;
        String code = result.getCode();
        String msg = result.getMessage();

        for (ScriptResultEnum value : values()) {
            if (value.code.equals(code) && value.message.equals(msg)) {
                return value;
            }
        }
        // 如果 success 为 true 但未匹配到具体枚举，返回通用 SUCCESS
        if (result.isSuccess()) {
            return SUCCESS;
        }
        return UNKNOWN;
    }

    /**
     * 转换为 ApiResult（无数据）
     */
    public <T> ApiResult<T> toApiResult() {
        if (this.success) {
            return ApiResult.success();
        } else {
            return ApiResult.error(this.httpStatus, this.defaultDesc);
        }
    }

    /**
     * 转换为 ApiResult，可附加自定义消息（用于携带 detail 信息）
     */
    public <T> ApiResult<T> toApiResult(String customMessage) {
        if (this.success) {
            ApiResult<T> result = ApiResult.success();
            result.setMessage(customMessage);
            return result;
        } else {
            return ApiResult.error(this.httpStatus, customMessage);
        }
    }
}