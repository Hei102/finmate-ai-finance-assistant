package com.zth.java.ai.langchain4j.service;

import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/** 使用随机盐和 PBKDF2 保存密码散列，并校验登录密码，不存储明文密码。 */
@Service
public class PasswordService {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String HASH_PREFIX = "pbkdf2_sha256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    /** 生成随机盐，将算法标识、迭代次数、盐和散列拼接保存。 */
    public String hash(String rawPassword) {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        byte[] hash = pbkdf2(rawPassword.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        return String.join("$",
                HASH_PREFIX,
                String.valueOf(ITERATIONS),
                Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(hash)
        );
    }

    /** 按保存的参数重新计算散列，并使用常量时间比较验证密码。 */
    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        String[] parts = storedHash.split("\\$");
        if (parts.length != 4 || !HASH_PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
            byte[] actualHash = pbkdf2(rawPassword.toCharArray(), salt, iterations, expectedHash.length * 8);
            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** 调用 Java 密码学 API 派生密码散列。 */
    private byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength) {
        try {
            KeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("密码处理失败", exception);
        }
    }
}
