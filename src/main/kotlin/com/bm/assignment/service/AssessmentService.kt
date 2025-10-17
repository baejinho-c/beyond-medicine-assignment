package com.bm.assignment.service

import com.bm.assignment.api.dto.*
import com.bm.assignment.domain.*
import com.bm.assignment.repo.DailyAssessmentRepository
import com.bm.assignment.repo.PrescriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.math.round
import java.time.Clock
import com.bm.assignment.api.exception.DuplicateDailyAssessmentException

/**
 * AssessmentService
 *
 * - 일일 평가 등록 및 주차별 통계(Weekly Trend) 계산의 핵심 비즈니스 로직을 담당한다.
 * - 도메인 규칙은 Prescription.status()와 DailyAssessmentRepository를 기반으로 검증 및 집계된다.
 */
@Service
class AssessmentService(
    private val prescriptionRepo: PrescriptionRepository,
    private val assessmentRepo: DailyAssessmentRepository,
    private val clock: Clock
) {

    /**
     * 일일 평가 등록
     *
     * 1. 처방 존재 여부 및 상태 유효성 검사
     * 2. 활성 기간(D0~D+41) 내에서만 등록 허용
     * 3. 하루 1회 제한(중복 검사 차단)
     * 4. 미래 날짜/통증 부위 중복 검증
     * 5. 주차 계산 후 Assessment + Pain 엔티티 생성 및 저장
     *
     * 트랜잭션: 신규 평가 등록(쓰기) 트랜잭션
     */
    @Transactional
    fun registerDailyAssessment(req: DailyAssessmentRequest): DailyAssessmentResponse {
        // 1) 처방 코드 유효성 검사
        val prescription = prescriptionRepo.findByCode(req.prescriptionCode)
            ?: throw IllegalArgumentException("invalid prescriptionCode")

        // 2) 활성 기간 유효성 검증
        val nowDate = req.date
        val statusOnDay = prescription.status(nowDate)
        if (statusOnDay != PrescriptionStatus.ACTIVE) {
            throw IllegalStateException("assessment allowed only during ACTIVE period")
        }

        val activated = prescription.activatedDate!!
        if (nowDate.isBefore(activated) || nowDate.isAfter(activated.plusDays(41))) {
            throw IllegalStateException("assessment date must be within activation window")
        }

        // 3) 하루 1회 제한: 중복 검사 차단
        if (assessmentRepo.existsByPrescriptionIdAndDate(prescription.id!!, req.date)) {
            throw DuplicateDailyAssessmentException("only one assessment per day is allowed")
        }

        // 4) 미래 날짜 방어
        val today = LocalDate.now(clock)
        if (req.date.isAfter(today)) {
            throw IllegalArgumentException("future date is not allowed")
        }

        // 5) 통증 부위 중복 방지
        val distinctLocations = req.pains.map { it.location }.toSet()
        if (distinctLocations.size != req.pains.size) {
            throw IllegalArgumentException("duplicate pain locations are not allowed")
        }

        // 6) 주차 계산: 활성일로부터 일수 차이 / 7 + 1
        val days = java.time.temporal.ChronoUnit.DAYS.between(activated, req.date).toInt()
        val week = (days / 7) + 1 // 1..6

        // 7) 도메인 객체 생성
        val assessment = DailyAssessment(
            prescription = prescription,
            date = req.date,
            week = week,
            painScore = req.painScore,
            stressScore = req.stressScore,
            functionScore = req.functionScore,
            pains = mutableListOf()
        )

        // 통증 부위 엔티티 생성 및 연관 관계 설정
        req.pains.forEach {
            assessment.pains.add(
                AssessmentPain(
                    assessment = assessment,
                    location = it.location,
                    intensity = it.intensity,
                    note = it.note
                )
            )
        }

        // 8) 저장 및 응답 변환
        val saved = assessmentRepo.saveAndFlush(assessment)
        return DailyAssessmentResponse(
            assessmentId = saved.id!!,
            prescriptionCode = prescription.code,
            week = week
        )
    }

    /**
     * 주차별 트렌드 조회
     *
     * - 처방 코드 기준으로 특정 주차 범위(1~6)를 조회
     * - 각 주차별 평균 Pain/Stress/Function 점수 계산
     * - 이전 주차 대비 변화율(ChangeRate) 계산
     * - 상위 통증 부위 Top 3 집계
     *
     * 트랜잭션: 읽기 전용
     */
    @Transactional(readOnly = true)
    fun weeklyTrend(prescriptionCode: String, startWeek: Int?, endWeek: Int?): WeeklyTrendResponse {
        val prescription = prescriptionRepo.findByCode(prescriptionCode)
            ?: throw IllegalArgumentException("Invalid prescriptionCode")

        val start = startWeek ?: 1
        val endDefault = currentWeek(prescription, LocalDate.now(clock))
        val end = endWeek ?: endDefault

        // 입력 검증
        if (start !in 1..6) throw IllegalArgumentException("startWeek must be between 1 and 6")
        if (end !in 1..6) throw IllegalArgumentException("endWeek must be between 1 and 6")
        if (start > end) throw IllegalArgumentException("startWeek must be <= endWeek")

        // N+1 방지: pains fetch join 포함
        val list = assessmentRepo.findRangeWithPains(prescription.id!!, start, end)

        // 주차별 그룹화 및 평균 계산
        val grouped = list.groupBy { it.week }.toSortedMap()
        val weeklyItems = mutableListOf<WeeklyTrendItem>()
        var lastWithData: WeeklyTrendItem? = null

        grouped.forEach { (week, items) ->
            val avgPain = items.map { it.painScore }.average()
            val avgStress = items.map { it.stressScore }.average()
            val avgFunc = items.map { it.functionScore }.average()

            // 변화율 계산 (이전 주 대비 개선/악화)
            val change = lastWithData?.let { prev ->
                ChangeRate(
                    pain = rate(prev.avgPain, avgPain, lowerIsBetter = true),
                    stress = rate(prev.avgStress, avgStress, lowerIsBetter = true),
                    function = rate(prev.avgFunction, avgFunc, lowerIsBetter = false)
                )
            }

            val item = WeeklyTrendItem(
                week = week,
                avgPain = round1(avgPain),
                avgStress = round1(avgStress),
                avgFunction = round1(avgFunc),
                changeRate = change
            )
            weeklyItems.add(item)
            lastWithData = item
        }

        // 상위 통증 부위 Top3 계산
        val pains = list.flatMap { it.pains }
        val top = pains.groupBy { it.location }
            .map { (loc, ps) ->
                TopPainLocation(
                    location = loc,
                    count = ps.size.toLong(),
                    avgIntensity = round1(ps.map { it.intensity }.average())
                )
            }
            .sortedWith(
                compareByDescending<TopPainLocation> { it.count }
                    .thenByDescending { it.avgIntensity }
            )
            .take(3)

        return WeeklyTrendResponse(
            prescriptionCode = prescription.code,
            period = PeriodRange(startWeek = start, endWeek = end),
            weeklyTrend = weeklyItems,
            topPainLocations = top
        )
    }

    /**
     * 변화율 계산
     * - prev → curr 간의 상대 변화율(%)
     * - 낮을수록 좋은 지표(lowerIsBetter=true)는 감소율을 개선으로 간주
     */
    private fun rate(prev: Double, curr: Double, lowerIsBetter: Boolean): Double {
        if (prev == 0.0) return 0.0
        val raw = if (lowerIsBetter) (prev - curr) / prev * 100.0 else (curr - prev) / prev * 100.0
        return round1(raw)
    }

    /** 소수점 1자리 반올림 */
    private fun round1(v: Double): Double = kotlin.math.round(v * 10.0) / 10.0

    /**
     * 현재 주차 계산
     * - 활성 상태 또는 완료된 처방만 주차 계산 대상
     * - 활성일 기준 일수 차이를 7로 나눈 뒤 1~6 사이로 보정
     */
    private fun currentWeek(p: Prescription, now: LocalDate): Int {
        val status = p.status(now)
        return if (status == PrescriptionStatus.ACTIVE || status == PrescriptionStatus.COMPLETED) {
            val base = p.activatedDate!!
            val days = java.time.temporal.ChronoUnit.DAYS.between(base, now).toInt()
            ((days / 7) + 1).coerceIn(1, 6)
        } else 1
    }
}
