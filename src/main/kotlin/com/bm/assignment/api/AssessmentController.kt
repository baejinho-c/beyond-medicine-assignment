package com.bm.assignment.api

import com.bm.assignment.api.dto.DailyAssessmentRequest
import com.bm.assignment.api.dto.DailyAssessmentResponse
import com.bm.assignment.api.dto.WeeklyTrendResponse
import com.bm.assignment.service.AssessmentService
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.validation.annotation.Validated
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ProblemDetail
import java.net.URI

/**
 * AssessmentController
 *
 * - 일일 평가 등록 및 주차별 트렌드 조회를 위한 REST API 진입점.
 * - @Validated: 요청 파라미터 수준의 검증 활성화 (RequestBody 외부 @RequestParam에도 적용)
 * - @RestController: JSON 직렬화 응답 + 예외는 GlobalExceptionHandler에서 처리됨.
 */
@RestController
@RequestMapping("/api/v1/assessments")
@Validated
class AssessmentController(
    private val service: AssessmentService
) {
    /**
     * [POST] /api/v1/assessments/daily
     *
     * 일일 평가 등록 엔드포인트.
     * - 요청 DTO(DailyAssessmentRequest)에 @Valid 적용 → 필드 제약(0~10, 코드 패턴 등) 자동 검증.
     * - 정상 등록 시 201 Created + Location 헤더 반환.
     * - 중복 시(하루 1회 제한) 409 Conflict 반환.
     *
     * Swagger 문서:
     *  - @Operation: API 설명
     *  - @ApiResponse: 상태 코드별 응답 스펙 명시 (ProblemDetail 구조 포함)
     */
    @PostMapping("/daily")
    @Operation(summary = "Register daily assessment")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(
        responseCode = "400",
        description = "Validation error",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    @ApiResponse(
        responseCode = "409",
        description = "Duplicate assessment",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    fun createDaily(@Valid @RequestBody req: DailyAssessmentRequest): ResponseEntity<DailyAssessmentResponse> {
        val body = service.registerDailyAssessment(req)
        // Location 헤더 예: /api/v1/assessments/15
        return ResponseEntity.created(URI.create("/api/v1/assessments/${body.assessmentId}"))
            .body(body)
    }

    /**
     * [GET] /api/v1/assessments/weekly-trend
     *
     * 주차별 통계 및 통증 부위 상위 3개 집계 반환.
     * - 처방코드 검증(@Pattern): 8자리 / 대문자4 + 숫자4
     * - 주차 범위(startWeek, endWeek): 1~6 제한 (@Min/@Max)
     * - start/end 생략 시 Service 내부에서 현재 주차 기준 자동 계산.
     *
     * 성공 시 200 OK + WeeklyTrendResponse(JSON)
     * 유효성 오류 시 GlobalExceptionHandler → ProblemDetail(400)
     */
    @GetMapping("/weekly-trend")
    @Operation(summary = "Weekly trend aggregation")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(
        responseCode = "400",
        description = "Validation error",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    fun weeklyTrend(
        @RequestParam
        @Pattern(
            regexp = "^(?=.*[A-Z]{4})(?=.*\\d{4})[A-Z0-9]{8}$",
            message = "Prescription code must be 8 chars with 4 letters & 4 digits"
        )
        prescriptionCode: String,

        @RequestParam(required = false)
        @Min(1) @Max(6)
        startWeek: Int?,

        @RequestParam(required = false)
        @Min(1) @Max(6)
        endWeek: Int?
    ): ResponseEntity<WeeklyTrendResponse> {
        return ResponseEntity.ok(service.weeklyTrend(prescriptionCode, startWeek, endWeek))
    }
}
