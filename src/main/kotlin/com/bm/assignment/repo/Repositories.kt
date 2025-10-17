package com.bm.assignment.repo

import com.bm.assignment.domain.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

/**
 * PrescriptionRepository
 *
 * - 처방 코드(code)로 단건 조회하는 기능을 제공한다.
 * - code는 유니크 인덱스가 있으므로 findByCode로 빠르게 조회 가능.
 */
interface PrescriptionRepository : JpaRepository<Prescription, Long> {
    fun findByCode(code: String): Prescription?
}

/**
 * DailyAssessmentRepository
 *
 * - 일일 평가 조회/존재 여부 확인/범위 조회를 위한 메서드 모음.
 * - 주차(week) 기반 집계/그래프 API가 있으므로, 범위 조회 시 정렬 기준(week asc, date asc)을 명시한다.
 */
interface DailyAssessmentRepository : JpaRepository<DailyAssessment, Long> {

    /**
     * 동일 처방/날짜 조합의 존재 여부 확인
     * - 애플리케이션 레벨에서 사전 차단(1일 1회 규칙) 용도로 사용
     * - DB 유니크 제약과 함께 이중 가드 역할
     */
    fun existsByPrescriptionIdAndDate(prescriptionId: Long, date: LocalDate): Boolean

    /**
     * 주차 범위 조회 (week between)
     * - 기본 메서드 이름 파생 쿼리
     * - 정렬: week 오름차순
     * - pains가 필요한 경우에는 아래 findRangeWithPains 사용 (fetch join)
     */
    fun findByPrescriptionIdAndWeekBetweenOrderByWeekAsc(
        prescriptionId: Long,
        start: Int,
        end: Int
    ): List<DailyAssessment>

    /**
     * 주차 범위 조회 (명시적 JPQL)
     * - 정렬: week asc, date asc
     * - pains는 로딩하지 않음 (N+1 방지 필요 시 withPains 사용)
     * - 그래프/통계용으로 주-일 순서가 보장되어야 하므로 정렬을 명시한다.
     */
    @Query(
        """
        select a from DailyAssessment a
        where a.prescription.id = :prescriptionId and a.week between :start and :end
        order by a.week asc, a.date asc
        """
    )
    fun findRange(
        prescriptionId: Long,
        start: Int,
        end: Int
    ): List<DailyAssessment>

    /**
     * 주차 범위 + pains까지 즉시 로딩(fetch join)
     * - 통증 부위 통계(top pain locations) 계산 시 N+1 방지용
     * - distinct로 중복 Row 제거
     * - 정렬: week asc, date asc (그래프 시계열 일관성)
     *
     * 주의:
     * - fetch join 컬렉션은 페이징과 함께 사용 불가 (Spring Data JPA 제한)
     * - 데이터 양이 많으면 별도 DTO 프로젝션으로 전환 고려
     */
    @Query(
        """
        select distinct a from DailyAssessment a
        left join fetch a.pains p
        where a.prescription.id = :prescriptionId and a.week between :start and :end
        order by a.week asc, a.date asc
        """
    )
    fun findRangeWithPains(
        prescriptionId: Long,
        start: Int,
        end: Int
    ): List<DailyAssessment>
}
