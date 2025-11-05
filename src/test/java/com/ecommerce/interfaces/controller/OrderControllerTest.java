package com.ecommerce.interfaces.controller;

import com.ecommerce.application.dto.OrderHistoryResponse;
import com.ecommerce.application.dto.OrderRequest;
import com.ecommerce.application.dto.OrderResponse;
import com.ecommerce.application.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@DisplayName("OrderController 테스트")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @Test
    @DisplayName("주문을 생성한다")
    void createOrder() throws Exception {
        // given
        OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest(1L, 2);
        OrderRequest request = new OrderRequest(1L, Arrays.asList(itemReq), null, null);
        OrderResponse response = new OrderResponse(1L, "ORD-20250101-00001", 100000, 0, 0, 100000);
        when(orderService.createOrder(any(OrderRequest.class))).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1L))
                .andExpect(jsonPath("$.orderNumber").value("ORD-20250101-00001"))
                .andExpect(jsonPath("$.totalAmount").value(100000))
                .andExpect(jsonPath("$.finalAmount").value(100000));
    }

    @Test
    @DisplayName("사용자의 주문 내역을 조회한다")
    void getOrderHistory() throws Exception {
        // given
        OrderHistoryResponse.OrderItemInfo item = new OrderHistoryResponse.OrderItemInfo(
                1L, "키보드", 2, 50000, "PENDING"
        );
        OrderHistoryResponse history = new OrderHistoryResponse(
                1L, "ORD-20250101-00001", 100000, LocalDateTime.now(), Collections.singletonList(item)
        );
        when(orderService.getOrderHistory(1L)).thenReturn(Arrays.asList(history));

        // when & then
        mockMvc.perform(get("/api/orders/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(1L))
                .andExpect(jsonPath("$[0].orderNumber").value("ORD-20250101-00001"))
                .andExpect(jsonPath("$[0].totalAmount").value(100000))
                .andExpect(jsonPath("$[0].items[0].productName").value("키보드"));
    }
}
