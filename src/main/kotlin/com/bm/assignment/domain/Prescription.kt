package com.bm.assignment.domain

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Prescription (처방 엔티티)
 *
 * 환자의 치료 처방 정보를 나타내는 도메인
 * 
 * - 각 처방은 고유한 code(8자리)로 식별된다.
 * - createdAt(발행일)과 activatedDate(활성화일)을 기반으로
 *   현재 상태(WAITING / ACTIVE / COMPLETED / EXPIRED)를 계산
 *
 * 비즈니스 로직:
 *  - 활성화일(activatedDate)이 존재하면 '활성 처방'
 *  - 미활성 상태로 6주 이상 경과하면 '만료(EXPIRED)'
 *  - 활성화 후 42일(D+42) 이후에는 '완료(COMPLETED)'
 */
@Entity
@Table(
    name = "prescriptions",
    indexes = [Index(columnList = "code", unique = true)]
)
class Prescription(

    /** PK (자동 증가 ID) */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    /**
     * 처방 코드 (8자리, A-Z + 0-9 조합)
     *
     * - Unique Index 지정으로 중복 방지
     * - 실제 진료/등록 시 QR 또는 문자 코드로 사용됨
     */
    @Column(nullable = false, unique = true, length = 8)
    val code: String,

    /**
     * 처방 생성(발행) 일시
     *
     * - 처방이 시스템에 등록된 시점
     * - 만료(EXPIRED) 계산의 기준이 됨
     */
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 처방 활성화(시작) 날짜
     *
     * - 실제 치료가 시작된 날짜
     * - 주차(week) 및 ACTIVE 기간 계산의 기준
     * - null이면 아직 활성화되지 않은 상태로 간주됨
     */
    val activatedDate: LocalDate? = null
) {
    /**
     * 처방 상태 계산 메서드
     *
     * 활성화일(activatedDate)과 현재 날짜(nowDate)를 비교하여
     * 현재 처방의 상태를 동적으로 산출
     *
     * 상태 정의:
     *  - WAITING  : 활성화 전이거나, 활성화일이 아직 도래하지 않은 상태
     *  - ACTIVE   : 활성화일로부터 D+41일까지 (6주차 내)
     *  - COMPLETED: 활성화일로부터 42일 이후
     *  - EXPIRED  : 한 번도 활성화되지 않고 6주 이상 경과한 경우
     *
     * 참고:
     *  - 날짜 비교는 LocalDate 기준 (KST)
     *  - endActive: D+41 (마지막 ACTIVE일)
     *  - afterComplete: D+42 (완료 상태 전환 시점)
     */
    fun status(nowDate: LocalDate = LocalDate.now()): PrescriptionStatus {
        return if (activatedDate != null) {
            val endActive = activatedDate.plusDays(41)     // D+41일까지 ACTIVE
            val afterComplete = activatedDate.plusDays(42) // D+42부터 COMPLETED
            when {
                nowDate.isBefore(activatedDate) -> PrescriptionStatus.WAITING // 활성화 전 (예외적 상황)
                !nowDate.isAfter(endActive) -> PrescriptionStatus.ACTIVE
                else -> PrescriptionStatus.COMPLETED
            }
        } else {
            // 활성화되지 않은 처방의 만료 계산
            val createdDate = createdAt.toLocalDate()
            if (nowDate.isAfter(createdDate.plusWeeks(6))) PrescriptionStatus.EXPIRED
            else PrescriptionStatus.WAITING
        }
    }
}

/**
 * 처방 상태 Enum
 *
 * WAITING   : 활성화 전 또는 대기 상태
 * ACTIVE    : 치료 진행 중
 * COMPLETED : 치료 완료 (활성화 후 6주 초과)
 * EXPIRED   : 비활성 상태로 6주 이상 경과
 */
enum class PrescriptionStatus {
    WAITING, ACTIVE, COMPLETED, EXPIRED
}
