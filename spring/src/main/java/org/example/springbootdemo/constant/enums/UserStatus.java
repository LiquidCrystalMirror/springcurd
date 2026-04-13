package org.example.springbootdemo.constant.enums;

/**
 * 用户状态枚举
 */
public enum UserStatus {
    /**
     * 禁用
     */
    DISABLED((byte) 0, "禁用"),
    
    /**
     * 启用
     */
    ENABLED((byte) 1, "启用");

    private final byte code;
    private final String description;

    UserStatus(byte code, String description) {
        this.code = code;
        this.description = description;
    }

    public byte getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据代码获取枚举
     * @param code 状态代码
     * @return 用户状态枚举
     */
    public static UserStatus getByCode(byte code) {
        for (UserStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的用户状态：" + code);
    }

}
