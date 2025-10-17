package com.bm.assignment.domain

import jakarta.persistence.*
import java.time.LocalDate

/**
 * DailyAssessment
 *
 * 일일 평가(Daily Assessment)의 도메인 엔티티.
 * 
 * 하나의 처방(Prescription)에 대해 특정 날짜(date)에 수행된 평가 데이터를 저장한다.
 * 통증, 스트레스, 기능 점수 등의 수치를 포함하며,
 * 각 평가에는 여러 통증 부위(AssessmentPain)가 연관될 수 있다.
 *
 * 제약 조건:
 *  - prescription_id + date 조합은 유일해야 함 (하루에 한 번만 평가 가능)
 *  - 주차(week)는 처방 활성일 기준으로 계산됨
 */
@Entity
@Table(
    name = "daily_assessments",
    uniqueConstraints = [UniqueConstraint(columnNames = ["prescription_id", "date"])],
    indexes = [
        Index(columnList = "prescription_id,date"),
        Index(columnList = "prescription_id,week,date")
    ]
)
class DailyAssessment(

    /** PK (자동 증가 ID) */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    /**
     * 처방(Prescription)과의 다대일(ManyToOne) 관계
     * 
     * - 하나의 처방에 대해 여러 개의 일일 평가가 존재할 수 있다.
     * - 지연 로딩(LAZY)을 사용하여 필요할 때만 로드.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescription_id", nullable = false)
    val prescription: Prescription,

    /** 평가 날짜 (요청에서 지정 가능, LocalDate 기준) */
    @Column(nullable = false)
    val date: LocalDate,

    /**
     * 처방 활성일 기준 주차 정보
     * 
     * - 처방 활성일(activatedDate) 기준 +1주차부터 시작
     * - 통계/트렌드 분석 시 주차 단위 집계에 활용됨
     */
    @Column(nullable = false)
    val week: Int,

    /** 통증 점수 (0~10 범위) */
    @Column(nullable = false)
    val painScore: Int,

    /** 스트레스 점수 (0~10 범위) */
    @Column(nullable = false)
    val stressScore: Int,

    /** 기능 점수 (0~10 범위) */
    @Column(nullable = false)
    val functionScore: Int,

    /**
     * 통증 부위 리스트
     * 
     * - 일대다(OneToMany) 관계
     * - cascade = ALL: 부모 저장 시 자식(통증 부위)도 함께 저장
     * - orphanRemoval = true: 부모에서 제거 시 자식 레코드도 삭제
     */
    @OneToMany(mappedBy = "assessment", cascade = [CascadeType.ALL], orphanRemoval = true)
    val pains: MutableList<AssessmentPain> = mutableListOf()
)

/**
 * AssessmentPain
 *
 * 통증 부위(PainRegion)와 해당 부위의 강도(intensity)를 표현하는 엔티티.
 * DailyAssessment와 다대일 관계를 가진다.
 */
@Entity
@Table(name = "assessment_pains")
class AssessmentPain(

    /** PK (자동 증가 ID) */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    /**
     * 상위 평가(DailyAssessment)와의 다대일 관계
     * 
     * - 하나의 평가에는 여러 통증 부위가 존재할 수 있음.
     * - 지연 로딩으로 설정하여 성능 최적화.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    val assessment: DailyAssessment,

    /**
     * 통증 부위 (enum 기반)
     * LEFT_JAW, RIGHT_JAW 등 문자열로 저장
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val location: PainRegion,

    /** 통증 강도 (0~10) */
    @Column(nullable = false)
    val intensity: Int,

    /** 부가 설명 (선택 사항, 예: '식사 후 통증') */
    @Column
    val note: String? = null
)

/**
 * PainRegion
 *
 * 통증이 발생한 신체 부위를 정의하는 열거형(Enum).
 * - 문자열(EnumType.STRING)로 DB에 저장된다.
 * 1. LEFT_JAW: 좌측 턱 관절
 * 2. RIGHT_JAW: 우측 턱 관절
 * 3. LEFT_TEMPLE: 좌측 관자놀이
 * 4. RIGHT_TEMPLE: 우측 관자놀이
 * 5. NECK: 목
 * 6. CHIN: 턱 끝

 */
enum class PainRegion {
    LEFT_JAW, RIGHT_JAW, LEFT_TEMPLE, RIGHT_TEMPLE, NECK, CHIN
}
