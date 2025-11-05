package com.ecommerce.domain.repository;

import com.ecommerce.domain.entity.Order;

import java.util.List;
import java.util.Optional;

/**
 * 주문 Repository 인터페이스
 */
public interface OrderRepository {

    /**
     * 주문을 저장합니다.
     *
     * @param order 저장할 주문
     * @return 저장된 주문
     */
    Order save(Order order);

    /**
     * ID로 주문을 조회합니다.
     *
     * @param id 주문 ID
     * @return 주문 Optional
     */
    Optional<Order> findById(Long id);

    /**
     * 사용자 ID로 주문 목록을 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 주문 목록
     */
    List<Order> findByUserId(Long userId);
}
