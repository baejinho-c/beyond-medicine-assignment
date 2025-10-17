package com.bm.assignment.repo

import com.bm.assignment.domain.DailyAssessment
import com.bm.assignment.domain.Prescription
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * RepositoriesDataJpaTests
 *
 * - Spring Data JPA 레이어 단위 테스트.
 * - H2 인메모리 DB에서 Repository 동작을 실제 쿼리 수준까지 검증한다.
 * - EntityManager, 트랜잭션 컨텍스트가 자동 구성된다.
 */
@DataJpaTest
class RepositoriesDataJpaTests(
    @Autowired val prescriptions: PrescriptionRepository,
    @Autowired val assessments: DailyAssessmentRepository
) {

    /**
     * 케이스: 동일 처방(prescription) + 동일 날짜(date)의 평가가 존재하는지 확인
     *
     * 시나리오:
     * 1. 새로운 Prescription 생성 및 저장
     * 2. 해당 처방에 DailyAssessment를 1건 등록
     * 3. existsByPrescriptionIdAndDate() 호출 시 true 반환을 검증
     *
     * 목적:
     * - "하루 1회 평가" 제약이 DB 쿼리 기반으로 정상적으로 동작하는지 확인한다.
     * - 단순 존재 여부 체크 메서드가 Spring Data JPA 파생 쿼리 형태로 올바르게 작동하는지 테스트.
     */
    @Test
    fun `unique daily per prescription and date`() {
        // 1) 테스트용 처방 저장
        val p = prescriptions.save(
            Prescription(
                code = "TT11YY22",
                createdAt = LocalDateTime.now(),
                activatedDate = LocalDate.now()
            )
        )

        // 2) 일일 평가(DailyAssessment) 저장
        assessments.save(
            DailyAssessment(
                prescription = p,
                date = LocalDate.now(),
                week = 1,
                painScore = 1,
                stressScore = 1,
                functionScore = 1
            )
        )

        // 3) 존재 여부 검사 (하루 1회 제약 검증)
        val exists = assessments.existsByPrescriptionIdAndDate(p.id!!, LocalDate.now())
        assertTrue(exists)
    }
}
