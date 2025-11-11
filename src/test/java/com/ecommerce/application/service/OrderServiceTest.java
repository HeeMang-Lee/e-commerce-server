package com.ecommerce.application.service;

import com.ecommerce.application.dto.*;
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

        when(userRepository.getByIdOrThrow(1L)).thenReturn(user);

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

        // when
        OrderResponse response = orderService.createOrder(request);

        // then
        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.totalAmount()).isEqualTo(100000);
        assertThat(response.discountAmount()).isEqualTo(0);
        assertThat(response.usedPoint()).isEqualTo(0);
        assertThat(response.finalAmount()).isEqualTo(100000);
        assertThat(product.getStockQuantity()).isEqualTo(8); // 재고 차감 확인

        // 주문 생성 시에는 포인트 차감, 쿠폰 사용, 외부 전송이 발생하지 않음
        verify(pointHistoryRepository, never()).save(any());
        verify(userCouponRepository, never()).save(any());
        verify(dataPlatformService, never()).sendOrderData(anyString());
        verify(popularProductRepository, never()).recordSale(any(), anyInt(), any());
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
        assertThat(history.get(0).orderId()).isEqualTo(1L);
        assertThat(history.get(0).totalAmount()).isEqualTo(100000);
        assertThat(history.get(0).items()).hasSize(1);
        assertThat(history.get(0).items().get(0).productName()).isEqualTo("키보드");
    }

    @Test
    @DisplayName("결제를 처리한다 - 포인트 사용")
    void processPayment_WithPoint() {
        // given
        User user = new User(1L, "테스트", "test@test.com", 10000);
        Product product = new Product(1L, "키보드", "무선", 50000, 8, "전자");

        List<OrderItem> items = new ArrayList<>();
        items.add(new OrderItem(product, 2));

        Order order = new Order(user.getId(), items);
        order.setId(1L);

        OrderPayment payment = new OrderPayment(1L, 100000, 0, 5000, null);

        PaymentRequest request = new PaymentRequest(null, 5000);

        when(orderRepository.getByIdOrThrow(1L)).thenReturn(order);
        when(userRepository.getByIdOrThrow(1L)).thenReturn(user);
        when(orderPaymentRepository.getByOrderIdOrThrow(1L)).thenReturn(payment);
        when(orderPaymentRepository.save(any(OrderPayment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pointHistoryRepository.save(any(PointHistory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        // when
        PaymentResponse response = orderService.processPayment(1L, request);

        // then
        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.usedPoint()).isEqualTo(5000);
        assertThat(response.paymentStatus()).isEqualTo("COMPLETED");
        assertThat(user.getPointBalance()).isEqualTo(5000); // 10000 - 5000

        verify(pointHistoryRepository, times(1)).save(any());
        verify(popularProductRepository, times(1)).recordSale(eq(1L), eq(2), any());
        verify(dataPlatformService, times(1)).sendOrderData(anyString());
    }
}
