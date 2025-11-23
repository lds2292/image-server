package com.ktlapha.imageserver.common.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * 전역 예외 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private String message;
    private Integer status;
    private Instant timestamp;
    private Map<String, String> errors; // 필드 단위 검증 오류 등 상세 정보
}
