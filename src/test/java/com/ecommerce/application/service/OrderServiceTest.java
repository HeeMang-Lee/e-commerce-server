package com.ecommerce.application.service;

import com.ecommerce.application.dto.OrderHistoryResponse;
import com.ecommerce.application.dto.OrderRequest;
import com.ecommerce.application.dto.OrderResponse;
import com.ecommerce.domain.entity.*;
import com.ecommerce.domain.repository.*;
import com.ecommerce.infrastructure.external.DataPlatformService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private DataPlatformService dataPlatformService;

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

        // executeWithLock 모킹: Function을 받아서 실행
        when(productRepository.executeWithLock(eq(1L), any())).thenAnswer(invocation -> {
            var operation = invocation.getArgument(1, java.util.function.Function.class);
            return operation.apply(product);
        });

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });
        when(orderPaymentRepository.save(any(OrderPayment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        // when
        OrderResponse response = orderService.createOrder(request);

        // then
        assertThat(response.getOrderId()).isEqualTo(1L);
        assertThat(response.getTotalAmount()).isEqualTo(100000);
        assertThat(response.getDiscountAmount()).isEqualTo(0);
        assertThat(response.getUsedPoint()).isEqualTo(0);
        assertThat(response.getFinalAmount()).isEqualTo(100000);
        assertThat(product.getStockQuantity()).isEqualTo(8); // 재고 차감 확인
    }

    @Test
    @DisplayName("사용자의 주문 내역을 조회한다")
    void getOrderHistory() {
        // given
        User user = new User(1L, "테스트", "test@test.com", 10000);
        Product product = new Product(1L, "키보드", "무선", 50000, 10, "전자");

        List<OrderItem> items = new ArrayList<>();
        items.add(new OrderItem(product, 2));

        Order order = new Order(user.getId(), items);
        order.setId(1L);

        when(orderRepository.findByUserId(1L)).thenReturn(Arrays.asList(order));

        // when
        List<OrderHistoryResponse> history = orderService.getOrderHistory(1L);

        // then
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getOrderId()).isEqualTo(1L);
        assertThat(history.get(0).getTotalAmount()).isEqualTo(100000);
        assertThat(history.get(0).getItems()).hasSize(1);
        assertThat(history.get(0).getItems().get(0).getProductName()).isEqualTo("키보드");
    }
}
