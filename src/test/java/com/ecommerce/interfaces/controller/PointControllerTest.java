package com.ecommerce.interfaces.controller;

import com.ecommerce.application.dto.PointChargeRequest;
import com.ecommerce.application.dto.PointResponse;
import com.ecommerce.application.service.PointService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PointController.class)
@DisplayName("PointController 테스트")
class PointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PointService pointService;

    @Test
    @DisplayName("포인트를 충전한다")
    void chargePoint() throws Exception {
        // given
        PointChargeRequest request = new PointChargeRequest(1L, 10000);
        PointResponse response = new PointResponse(1L, 10000);
        when(pointService.chargePoint(any(PointChargeRequest.class))).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/points/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.balance").value(10000));
    }

    @Test
    @DisplayName("사용자의 포인트를 조회한다")
    void getPoint() throws Exception {
        // given
        PointResponse response = new PointResponse(1L, 5000);
        when(pointService.getPoint(1L)).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/points/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.balance").value(5000));
    }
}
