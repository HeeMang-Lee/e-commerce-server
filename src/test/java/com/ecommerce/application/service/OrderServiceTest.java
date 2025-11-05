package com.ecommerce.application.service;

import com.ecommerce.application.dto.OrderRequest;
import com.ecommerce.application.dto.OrderResponse;
import com.ecommerce.domain.entity.*;
import com.ecommerce.domain.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService 테스트")
class OrderServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderPaymentRepository orderPaymentRepository;
    @Mock
    private UserCouponRepository userCouponRepository;
    @Mock
    private PointHistoryRepository pointHistoryRepository;
    @Mock
    private PopularProductRepository popularProductRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("주문을 생성한다 - 쿠폰/포인트 없음")
    void createOrder_WithoutCouponAndPoint() {
        // given
        User user = new User(1L, "테스트", "test@test.com", 10000);
        Product product = new Product(1L, "키보드", "무선", 50000, 10, "전자");

        OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest(1L, 2);
        OrderRequest request = new OrderRequest(1L, Arrays.asList(itemReq), null, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(productRepository.findByIdWithLock(1L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });
        when(orderPaymentRepository.save(any(OrderPayment.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        OrderResponse response = orderService.createOrder(request);

        // then
        assertThat(response.getOrderId()).isEqualTo(1L);
        assertThat(response.getTotalAmount()).isEqualTo(100000);
        assertThat(response.getDiscountAmount()).isEqualTo(0);
        assertThat(response.getUsedPoint()).isEqualTo(0);
        assertThat(response.getFinalAmount()).isEqualTo(100000);
        verify(productRepository).save(product);
    }
}
