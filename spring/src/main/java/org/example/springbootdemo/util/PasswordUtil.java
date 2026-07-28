package org.example.springbootdemo.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

/**
 * 密码工具类 —— BCrypt + MD5 双模式
 * <p>面试亮点：</p>
 * <ul>
 *   <li>BCrypt 自适应哈希：内置盐值，自动抵抗彩虹表攻击</li>
 *   <li>工作量因子(cost=10)：可调节计算强度，抵抗暴力破解</li>
 *   <li>向后兼容：支持旧 MD5 密码的平滑迁移验证</li>
 * </ul>
 */
@Slf4j
@Component
public class PasswordUtil {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10); // cost=10

    /** MD5 盐值（仅用于旧密码兼容） */
    private String md5Salt;

    @Value("${md5.salt:}")
    public void setMd5Salt(String salt) {
        this.md5Salt = salt;
    }

    // ==================== 新密码：BCrypt ====================

    /**
     * BCrypt 加密（推荐）
     * <p>自动生成随机盐值，每次加密结果不同</p>
     */
    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /**
     * BCrypt 密码验证
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        // BCrypt 密文以 $2a$ 开头
        if (encodedPassword != null && encodedPassword.startsWith("$2")) {
            return encoder.matches(rawPassword, encodedPassword);
        }
        // 回退到 MD5 验证（旧密码兼容）
        return verifyMd5Password(rawPassword, encodedPassword);
    }

    // ==================== 旧密码兼容：MD5（标记为 @Deprecated） ====================

    /**
     * @deprecated 请使用 {@link #encode(String)} 替代，BCrypt 更安全
     */
    @Deprecated
    public String md5(String input) {
        return DigestUtils.md5DigestAsHex(input.getBytes());
    }

    /**
     * @deprecated 请使用 {@link #encode(String)} 替代，BCrypt 更安全
     */
    @Deprecated
    public String md5WithSalt(String input) {
        return DigestUtils.md5DigestAsHex((input + md5Salt).getBytes());
    }

    /**
     * MD5 密码验证（兼容旧数据）
     */
    private boolean verifyMd5Password(String rawPassword, String encryptedPassword) {
        if (encryptedPassword == null) return false;
        return md5WithSalt(rawPassword).equals(encryptedPassword);
    }

    /**
     * 通用验证方法（对外统一入口）
     */
    public boolean verifyPassword(String rawPassword, String encryptedPassword) {
        return matches(rawPassword, encryptedPassword);
    }
}
