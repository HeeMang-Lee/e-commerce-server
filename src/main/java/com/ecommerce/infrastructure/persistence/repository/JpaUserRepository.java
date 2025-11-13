package com.ecommerce.infrastructure.persistence.repository;

import com.ecommerce.domain.entity.User;
import com.ecommerce.domain.vo.Email;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaUserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(Email email);
}
