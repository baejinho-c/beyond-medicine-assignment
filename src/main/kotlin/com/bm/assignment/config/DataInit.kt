package com.bm.assignment.config

import com.bm.assignment.domain.Prescription
import com.bm.assignment.repo.PrescriptionRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 데이터 초기화 설정
 *
 * 로컬 개발 및 테스트 환경에서 사용할 샘플 처방(Prescription) 데이터를 미리 삽입한다.
 * 
 * 목적:
 *  - Swagger / Postman 테스트 시 유효한 처방코드를 바로 사용할 수 있도록 함
 *  - 서비스 로직(활성/비활성/완료 상태 판정 등)을 검증하는데 필요한 기본 데이터를 제공
 *
 * 조건:
 *  - 기존 데이터가 이미 존재하면 초기화 로직은 실행되지 않음
 */
@Configuration
class DataInit {

    /**
     * Prescription 샘플 데이터 시딩(Seeding)
     *
     * CommandLineRunner는 애플리케이션 실행 직후 한 번 실행
     * Clock을 주입받아 테스트 시점 제어가 가능하도록 구성
     */
    @Bean
    fun seedPrescriptions(
        prescriptionRepository: PrescriptionRepository,
        clock: Clock
    ) = CommandLineRunner {
        // 데이터가 이미 존재할 경우, 중복 삽입 방지
        if (prescriptionRepository.count() > 0) return@CommandLineRunner

        /**
         * 상태별 처방 샘플
         * - WAITING  : 아직 활성화(activatedDate)되지 않은 상태 (최근 생성)
         * - ACTIVE   : 활성화되어 평가(assessment) 등록이 가능한 상태 (활성화 후 6주 이내)
         * - COMPLETED: 6주 이상 경과한 처방 (활성기간 종료)
         * - EXPIRED  : 한 번도 활성화되지 않고 6주 이상 경과한 경우
         */

        // WAITING (생성 후 3일, 아직 활성화되지 않음)
        prescriptionRepository.save(
            Prescription(
                code = "ABCD5678",
                createdAt = LocalDateTime.now(clock).minusDays(3),
                activatedDate = null
            )
        )

        // ACTIVE (최근 활성화된 상태, 활성화 후 8일 경과)
        prescriptionRepository.save(
            Prescription(
                code = "ABCD1234",
                createdAt = LocalDateTime.now(clock).minusDays(10),
                activatedDate = LocalDate.now(clock).minusDays(8)
            )
        )

        // ACTIVE (활성화 후 10일 경과)
        prescriptionRepository.save(
            Prescription(
                code = "QWER2348",
                createdAt = LocalDateTime.now(clock).minusDays(12),
                activatedDate = LocalDate.now(clock).minusDays(10)
            )
        )

        // ACTIVE (활성화 후 41일 경과, 완료 임박)
        prescriptionRepository.save(
            Prescription(
                code = "WERT2588",
                createdAt = LocalDateTime.now(clock).minusDays(50),
                activatedDate = LocalDate.now(clock).minusDays(41)
            )
        )

        // COMPLETED (활성화된 지 7주 경과, 완료 상태)
        prescriptionRepository.save(
            Prescription(
                code = "EFGH5678",
                createdAt = LocalDateTime.now(clock).minusWeeks(8),
                activatedDate = LocalDate.now(clock).minusWeeks(7).minusDays(1)
            )
        )

        // EXPIRED (활성화되지 않은 채 7주 경과)
        prescriptionRepository.save(
            Prescription(
                code = "ZZYY8899",
                createdAt = LocalDateTime.now(clock).minusWeeks(7),
                activatedDate = null
            )
        )
    }
}
