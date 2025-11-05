package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.User;

import java.util.Optional;

/**
 * 사용자 Repository 인터페이스
 */
public interface UserRepository {

    /**
     * 사용자를 저장합니다.
     *
     * @param user 저장할 사용자
     * @return 저장된 사용자
     */
    User save(User user);

    /**
     * ID로 사용자를 조회합니다.
     *
     * @param id 사용자 ID
     * @return 사용자 Optional
     */
    Optional<User> findById(Long id);

    /**
     * 이메일로 사용자를 조회합니다.
     *
     * @param email 이메일
     * @return 사용자 Optional
     */
    Optional<User> findByEmail(String email);
}
