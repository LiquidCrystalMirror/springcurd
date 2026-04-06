package org.example.springbootdemo.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
@Component
public class PasswordUtil {
    private String salt;
    @Value("${md5.salt}")
    public void setSalt(String salt) {
        this.salt = salt;
    }
    public String md5(String input) {
        return DigestUtils.md5DigestAsHex(input.getBytes());
    }

    // 带盐值的 MD5（更安全）
    public String md5WithSalt(String input) {
        return DigestUtils.md5DigestAsHex((input + salt).getBytes());
    }

    public boolean verifyPassword(String rawPassword, String encryptedPassword) {
        return md5WithSalt(rawPassword).equals(encryptedPassword);
    }
}
