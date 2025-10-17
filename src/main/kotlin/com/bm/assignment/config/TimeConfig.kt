package com.bm.assignment.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

/**
 * 시간 관련 공통 설정
 *
 * 시스템 전역에서 동일한 기준 시간(Clock)을 사용하도록 정의한다.
 * 
 * - 기본 시스템 시간 대신 Clock Bean을 주입받아 사용하면,
 *   테스트 시점을 고정하거나(MockClock 활용) 타임존 차이로 인한 버그를 방지할 수 있다.
 * - 모든 날짜/시간 계산(LocalDate, LocalDateTime)은 이 Clock 기반으로 수행된다.
 *
 * 예: LocalDate.now(clock), LocalDateTime.now(clock)
 */
@Configuration
class TimeConfig {

    /**
     * 시스템 Clock Bean 등록
     *
     * 표준 타임존(Asia/Seoul)을 기준으로 현재 시간을 제공한다.
     * - 운영 환경: 실제 시스템 시간 사용
     * - 테스트 환경: Clock.fixed() 등으로 주입 가능
     */
    @Bean
    fun systemClock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))
}
