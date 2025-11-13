package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.User;

import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    default User getByIdOrThrow(Long id) {
        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + id));
    }

    Optional<User> findByEmail(String email);

    void deleteAll();
}
