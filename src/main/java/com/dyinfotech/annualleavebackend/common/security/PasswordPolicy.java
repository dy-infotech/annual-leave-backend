package com.dyinfotech.annualleavebackend.common.security;

import java.nio.charset.StandardCharsets;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PasswordPolicy {

    public static final int BCRYPT_MAX_BYTES = 72;

    public static boolean isBcryptEncodable(String password) {
        return password != null
                && password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;
    }
}
