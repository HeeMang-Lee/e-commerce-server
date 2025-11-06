package com.ecommerce.domain.vo;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 이메일을 나타내는 Value Object
 * 불변 객체로 설계되어 유효한 이메일 형식만 허용합니다.
 */
public class Email {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private final String value;

    private Email(String value) {
        if (value == null) {
            throw new IllegalArgumentException("이메일은 필수입니다");
        }
        String trimmed = value.trim();
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("유효하지 않은 이메일 형식입니다");
        }
        if (trimmed.contains("..")) {
            throw new IllegalArgumentException("유효하지 않은 이메일 형식입니다");
        }
        this.value = trimmed;
    }

    /**
     * 정적 팩토리 메서드
     */
    public static Email of(String value) {
        return new Email(value);
    }

    /**
     * 이메일 값을 반환합니다
     */
    public String getValue() {
        return value;
    }

    /**
     * 도메인 부분을 반환합니다 (예: "test@example.com" → "example.com")
     */
    public String getDomain() {
        int atIndex = value.indexOf('@');
        return value.substring(atIndex + 1);
    }

    /**
     * 로컬 부분을 반환합니다 (예: "test@example.com" → "test")
     */
    public String getLocalPart() {
        int atIndex = value.indexOf('@');
        return value.substring(0, atIndex);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Email email = (Email) o;
        return Objects.equals(value, email.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
