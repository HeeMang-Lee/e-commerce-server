package com.ecommerce.interfaces.controller;

import com.ecommerce.application.dto.CouponIssueRequest;
import com.ecommerce.application.dto.UserCouponResponse;
import com.ecommerce.application.service.CouponService;
import com.ecommerce.domain.entity.UserCouponStatus;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CouponController.class)
@DisplayName("CouponController 테스트")
class CouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CouponService couponService;

    @Test
    @DisplayName("쿠폰을 발급한다")
    void issueCoupon() throws Exception {
        // given
        CouponIssueRequest request = new CouponIssueRequest(1L, 1L);
        LocalDateTime now = LocalDateTime.now();
        UserCouponResponse response = new UserCouponResponse(
                1L, 1L, 1L, UserCouponStatus.AVAILABLE, now, now.plusDays(30)
        );
        when(couponService.issueCoupon(any(CouponIssueRequest.class))).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/coupons/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.couponId").value(1L))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("사용자의 보유 쿠폰 목록을 조회한다")
    void getUserCoupons() throws Exception {
        // given
        LocalDateTime now = LocalDateTime.now();
        UserCouponResponse coupon1 = new UserCouponResponse(
                1L, 1L, 1L, UserCouponStatus.AVAILABLE, now, now.plusDays(30)
        );
        UserCouponResponse coupon2 = new UserCouponResponse(
                2L, 1L, 2L, UserCouponStatus.AVAILABLE, now, now.plusDays(30)
        );
        when(couponService.getUserCoupons(1L)).thenReturn(Arrays.asList(coupon1, coupon2));

        // when & then
        mockMvc.perform(get("/api/coupons/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(1L))
                .andExpect(jsonPath("$[0].couponId").value(1L))
                .andExpect(jsonPath("$[1].id").value(2L));
    }
}
