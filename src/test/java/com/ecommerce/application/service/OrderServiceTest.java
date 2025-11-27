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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
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
    private OrderItemRepository orderItemRepository;
    @Mock
    private OrderPaymentRepository orderPaymentRepository;
    @Mock
    private UserCouponRepository userCouponRepository;
    @Mock
    private PointHistoryRepository pointHistoryRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private DataPlatformService dataPlatformService;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private PointService pointService;
    @Mock
    private CouponService couponService;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("주문을 생성한다 - 쿠폰/포인트 없음")
    void createOrder_WithoutCouponAndPoint() throws Exception {
        // given
        User user = new User(1L, "테스트", "test@test.com", 10000);
        Product product = new Product(1L, "키보드", "무선", 50000, 10, "전자");

        OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest(1L, 2);
        OrderRequest request = new OrderRequest(1L, Arrays.asList(itemReq), null, null);

        // Mock RedissonClient and RLock
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        when(userRepository.getByIdOrThrow(1L)).thenReturn(user);
        when(productRepository.getByIdOrThrow(1L)).thenReturn(product);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });
        when(orderItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
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

        // 락 획득 및 해제 검증
        verify(lock, times(1)).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        verify(lock, times(1)).unlock();
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
    void processPayment_WithPoint() throws Exception {
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

        // Mock RedissonClient and RLock
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        when(orderRepository.getByIdOrThrow(1L)).thenReturn(order);
        when(orderPaymentRepository.getByOrderIdOrThrow(1L)).thenReturn(payment);
        when(orderPaymentRepository.save(any(OrderPayment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dataPlatformService.sendOrderData(anyString())).thenReturn(true);

        // PointService Mock 설정
        when(pointService.deductPoint(eq(1L), eq(5000), anyString(), eq(1L)))
                .thenReturn(new PointResponse(1L, 5000));

        // when
        PaymentResponse response = orderService.processPayment(1L, request);

        // then
        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.usedPoint()).isEqualTo(5000);
        assertThat(response.paymentStatus()).isEqualTo("COMPLETED");

        verify(pointService, times(1)).deductPoint(eq(1L), eq(5000), anyString(), eq(1L));
        verify(dataPlatformService, times(1)).sendOrderData(anyString());
    }
}
