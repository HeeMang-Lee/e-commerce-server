package com.ecommerce.application.service;

import com.ecommerce.application.dto.*;
import com.ecommerce.domain.entity.*;
import com.ecommerce.domain.repository.*;
import com.ecommerce.domain.service.*;
import com.ecommerce.infrastructure.lock.DistributedLockExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

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
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private OrderPaymentRepository orderPaymentRepository;
    @Mock
    private UserCouponRepository userCouponRepository;
    @Mock
    private DistributedLockExecutor lockExecutor;

    // Domain Services
    @Mock
    private OrderDomainService orderDomainService;
    @Mock
    private ProductDomainService productDomainService;
    @Mock
    private PointDomainService pointDomainService;
    @Mock
    private CouponDomainService couponDomainService;
    @Mock
    private PaymentDomainService paymentDomainService;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        // lockExecutor가 supplier를 실행하도록 설정
        lenient().when(lockExecutor.executeWithLock(anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(1);
                    return supplier.get();
                });

        // Runnable 버전도 설정
        lenient().doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(lockExecutor).executeWithLock(anyString(), any(Runnable.class));
    }

    @Test
    @DisplayName("주문을 생성한다 - 쿠폰/포인트 없음")
    void createOrder_WithoutCouponAndPoint() {
        // given
        User user = new User(1L, "테스트", "test@test.com", 10000);
        Product product = new Product(1L, "키보드", "무선", 50000, 10, "전자");

        OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest(1L, 2);
        OrderRequest request = new OrderRequest(1L, Arrays.asList(itemReq), null, null);

        Order order = new Order(1L);
        order.setId(1L);
        order.assignOrderNumber();

        OrderItem orderItem = new OrderItem(product, 2);
        orderItem.setOrderId(1L);

        when(userRepository.getByIdOrThrow(1L)).thenReturn(user);
        when(orderDomainService.createOrder(1L)).thenReturn(order);
        when(productDomainService.reduceStock(1L, 2)).thenReturn(product);
        when(orderDomainService.createOrderItem(1L, product, 2)).thenReturn(orderItem);
        when(orderPaymentRepository.save(any(OrderPayment.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        OrderResponse response = orderService.createOrder(request);

        // then
        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.totalAmount()).isEqualTo(100000);
        assertThat(response.discountAmount()).isEqualTo(0);
        assertThat(response.usedPoint()).isEqualTo(0);
        assertThat(response.finalAmount()).isEqualTo(100000);

        verify(orderDomainService).createOrder(1L);
        verify(productDomainService).reduceStock(1L, 2);
        verify(orderDomainService).createOrderItem(1L, product, 2);
        verify(lockExecutor).executeWithLock(anyString(), any(Supplier.class));
    }

    @Test
    @DisplayName("사용자의 주문 내역을 조회한다")
    void getOrderHistory() {
        // given
        User user = new User(1L, "테스트", "test@test.com", 10000);
        Product product = new Product(1L, "키보드", "무선", 50000, 10, "전자");

        List<OrderItem> items = new ArrayList<>();
        OrderItem orderItem = new OrderItem(product, 2);
        orderItem.setOrderId(1L);
        items.add(orderItem);

        Order order = new Order(user.getId());
        order.setId(1L);

        when(orderRepository.findByUserId(1L)).thenReturn(Arrays.asList(order));
        when(orderItemRepository.findByOrderIdIn(Arrays.asList(1L))).thenReturn(items);

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
        OrderItem orderItem = new OrderItem(product, 2);
        orderItem.setOrderId(1L);
        items.add(orderItem);

        Order order = new Order(user.getId());
        order.setId(1L);

        OrderPayment payment = new OrderPayment(1L, 100000, 0, 5000, null);

        PaymentRequest request = new PaymentRequest(null, 5000);

        when(orderRepository.getByIdOrThrow(1L)).thenReturn(order);
        when(orderPaymentRepository.getByOrderIdOrThrow(1L)).thenReturn(payment);
        when(pointDomainService.deductPoint(eq(1L), eq(5000), anyString(), eq(1L)))
                .thenReturn(5000);

        // Mock payment complete
        when(paymentDomainService.completePayment(1L)).thenAnswer(inv -> {
            payment.complete();
            return payment;
        });

        // when
        PaymentResponse response = orderService.processPayment(1L, request);

        // then
        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.usedPoint()).isEqualTo(5000);
        assertThat(response.paymentStatus()).isEqualTo("COMPLETED");

        verify(pointDomainService, times(1)).deductPoint(eq(1L), eq(5000), anyString(), eq(1L));
        verify(paymentDomainService, times(1)).completePayment(1L);
        verify(lockExecutor).executeWithLock(anyString(), any(Supplier.class));
    }
}
