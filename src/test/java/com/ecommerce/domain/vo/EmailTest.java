package com.ecommerce.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Email Value Object 테스트")
class EmailTest {

    @Test
    @DisplayName("유효한 이메일을 생성한다")
    void createEmail() {
        // when
        Email email = Email.of("test@example.com");

        // then
        assertThat(email.getValue()).isEqualTo("test@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "test@example.com",
            "user.name@example.com",
            "user+tag@example.co.kr",
            "user_123@test-domain.com"
    })
    @DisplayName("다양한 형식의 유효한 이메일을 생성한다")
    void createEmail_ValidFormats(String emailValue) {
        // when & then
        assertThatCode(() -> Email.of(emailValue))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            " ",
            "invalid",
            "invalid@",
            "@example.com",
            "invalid@.com",
            "invalid@domain",
            "invalid..email@example.com"
    })
    @DisplayName("유효하지 않은 이메일 형식은 예외를 발생시킨다")
    void createEmail_InvalidFormats_ThrowsException(String emailValue) {
        // when & then
        assertThatThrownBy(() -> Email.of(emailValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 이메일 형식입니다");
    }

    @Test
    @DisplayName("null 이메일은 생성할 수 없다")
    void createEmail_Null_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> Email.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이메일은 필수입니다");
    }

    @Test
    @DisplayName("도메인 부분을 추출한다")
    void getDomain() {
        // given
        Email email = Email.of("test@example.com");

        // when
        String domain = email.getDomain();

        // then
        assertThat(domain).isEqualTo("example.com");
    }

    @Test
    @DisplayName("로컬 부분을 추출한다")
    void getLocalPart() {
        // given
        Email email = Email.of("test@example.com");

        // when
        String localPart = email.getLocalPart();

        // then
        assertThat(localPart).isEqualTo("test");
    }

    @Test
    @DisplayName("같은 이메일은 동등하다")
    void equalsEmail() {
        // given
        Email email1 = Email.of("test@example.com");
        Email email2 = Email.of("test@example.com");
        Email email3 = Email.of("other@example.com");

        // then
        assertThat(email1).isEqualTo(email2);
        assertThat(email1).isNotEqualTo(email3);
        assertThat(email1.hashCode()).isEqualTo(email2.hashCode());
    }

    @Test
    @DisplayName("toString은 이메일 값을 반환한다")
    void toStringEmail() {
        // given
        Email email = Email.of("test@example.com");

        // then
        assertThat(email.toString()).isEqualTo("test@example.com");
    }
}
