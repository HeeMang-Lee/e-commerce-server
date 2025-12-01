package com.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "포인트 이력 목록 응답")
public record PointHistoryListResponse(
    @Schema(description = "포인트 이력 목록")
    List<PointHistoryInfo> histories,

    @Schema(description = "전체 이력 수", example = "10")
    int totalCount
) {}
