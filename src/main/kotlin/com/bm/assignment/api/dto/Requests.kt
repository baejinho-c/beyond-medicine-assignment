package com.bm.assignment.api.dto

import com.bm.assignment.domain.PainRegion
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.time.LocalDate

/**
 * DailyAssessment 관련 요청/응답 DTO 모음
 *
 * 환자의 통증, 스트레스, 기능 점수를 기록하는 일일 평가 요청 모델과
 * 이를 기반으로 생성되는 주차별 통계 응답 모델을 정의한다.
 */
data class DailyAssessmentRequest(

    // 처방코드 정책:
    // - 대문자 4자 + 숫자 4자 (총 8자, 순서는 무관)
    // - 영문/숫자 외 문자는 허용하지 않음 (스캔/수기 입력 혼동 방지 목적)
    @field:Pattern(
        regexp = "^(?=.*[A-Z]{4})(?=.*\\d{4})[A-Z0-9]{8}$",
        message = "Prescription code must be 8 chars with 4 letters & 4 digits"
    )
    @Schema(
        description = "Prescription code (4 letters + 4 digits, any order)",
        example = "ABCD1234"
    )
    val prescriptionCode: String,

    // 평가 날짜 (KST 기준)
    // LocalDate 형식(yyyy-MM-dd)으로 전달
    @Schema(description = "Assessment date (KST)", example = "2025-10-15")
    val date: LocalDate,

    // 통증 점수 (0~10 범위)
    @field:Min(0) @field:Max(10)
    @Schema(description = "Pain score (0..10)", example = "6")
    val painScore: Int,

    // 스트레스 점수 (0~10 범위)
    @field:Min(0) @field:Max(10)
    @Schema(description = "Stress score (0..10)", example = "4")
    val stressScore: Int,

    // 기능 점수 (0~10 범위)
    @field:Min(0) @field:Max(10)
    @Schema(description = "Function score (0..10)", example = "7")
    val functionScore: Int,

    // 통증 부위 리스트
    // - 0~6개까지 입력 가능
    // - 각 항목은 PainInput으로 구성되며, @Valid로 하위 필드 검증이 전파됨
    @field:Size(min = 0, max = 6)
    @field:Valid
    @Schema(description = "Pain locations (0..6 items)")
    val pains: List<PainInput> = emptyList()
)

/**
 * 통증 부위 및 강도 입력 모델
 * DailyAssessmentRequest 내 pains 리스트의 개별 요소로 사용된다.
 */
data class PainInput(
    @Schema(
        description = "Pain location (enum value)",
        example = "RIGHT_TEMPLE"
    )
    val location: PainRegion,

    @field:Min(0) @field:Max(10)
    @Schema(description = "Pain intensity (0..10)", example = "5")
    val intensity: Int,

    @Schema(
        description = "Optional note for specific condition",
        example = "아침 기상 직후 통증"
    )
    val note: String? = null
)

/**
 * 일일 평가 응답 모델
 * 평가 등록 후 고유 ID와 계산된 주차 정보를 반환한다.
 */
data class DailyAssessmentResponse(
    val assessmentId: Long,
    val prescriptionCode: String,
    val week: Int
)

/**
 * 주차별 트렌드 응답 모델
 * 주차 단위로 평균 점수 및 변화율 정보를 제공한다.
 */
data class WeeklyTrendResponse(
    val prescriptionCode: String,
    val period: PeriodRange,
    val weeklyTrend: List<WeeklyTrendItem>,
    val topPainLocations: List<TopPainLocation>
)

/**
 * 평가 기간(주차 단위)
 */
data class PeriodRange(
    val startWeek: Int,
    val endWeek: Int
)

/**
 * 주차별 평균 점수 및 전주 대비 변화율 정보
 */
data class WeeklyTrendItem(
    val week: Int,
    val avgPain: Double,
    val avgStress: Double,
    val avgFunction: Double,
    val changeRate: ChangeRate? // 첫 주차는 null 가능
)

/**
 * 항목별 변화율 정보 (% 단위)
 */
data class ChangeRate(
    val pain: Double?,
    val stress: Double?,
    val function: Double?
)

/**
 * 가장 빈번하게 보고된 통증 부위 정보
 * 통증 부위별 발생 횟수 및 평균 강도를 포함한다.
 */
data class TopPainLocation(
    val location: PainRegion,
    val count: Long,
    val avgIntensity: Double
)
