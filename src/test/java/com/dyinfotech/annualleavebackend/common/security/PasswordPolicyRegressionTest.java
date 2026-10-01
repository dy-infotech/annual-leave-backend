package com.dyinfotech.annualleavebackend.common.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordPolicyRegressionTest {

    @Test
    void bcryptLimit_isMeasuredInUtf8BytesNotJavaCharacters() {
        String exactly72Bytes = "a".repeat(68) + "😀";
        String seventyThreeBytes = "a".repeat(69) + "😀";

        assertTrue(PasswordPolicy.isBcryptEncodable(exactly72Bytes));
        assertFalse(PasswordPolicy.isBcryptEncodable(seventyThreeBytes));
    }

    @Test
    void koreanPasswords_respectSameUtf8ByteBoundary() {
        assertTrue(PasswordPolicy.isBcryptEncodable("가".repeat(24)));
        assertFalse(PasswordPolicy.isBcryptEncodable("가".repeat(25)));
    }
}
