package com.bm.assignment

import com.bm.assignment.api.dto.DailyAssessmentRequest
import com.bm.assignment.api.dto.PainInput
import com.bm.assignment.domain.PainRegion
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@SpringBootTest
@AutoConfigureMockMvc
class AssessmentApiTests(
    @Autowired val mockMvc: MockMvc,
    @Autowired val mapper: ObjectMapper
) {
    /**
     * 성공 케이스: 정상 일일평가 등록 → 201
     * - 같은 날짜로 한 번 더 등록 시 중복(하루 1회 제한) 처리 → 409
     */
    @Test
    fun `register daily assessment success`() {
        val body = DailyAssessmentRequest(
            prescriptionCode = "ABCD1234",
            date = LocalDate.now().minusDays(1),
            painScore = 7, stressScore = 5, functionScore = 6,
            pains = listOf(
                PainInput(location = PainRegion.LEFT_JAW, intensity = 8, note = "chewing pain"),
                PainInput(location = PainRegion.RIGHT_TEMPLE, intensity = 6, note = null)
            )
        )
        val res = mockMvc.perform(
            post("/api/v1/assessments/daily")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(body))
        ).andExpect(status().isCreated) // 등록 성공 시 201
         .andExpect(jsonPath("$.prescriptionCode").value("ABCD1234"))
         .andReturn()

        // 동일 날짜 재등록 → 409 Conflict 기대
        mockMvc.perform(
            post("/api/v1/assessments/daily")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(body))
        ).andExpect(status().isConflict)
    }

    /**
     * 경계 케이스: 활성 기간 D0, D+41은 허용 / D+42는 거부
     * - D0: 활성 시작일
     * - D+41: 활성 마지막 날
     * - D+42: 완료 상태 전환 + 미래 날짜(이 테스트 조건) → 400
     *
     * 상태코드는 /api/v1/assessments/daily 호출시 201로 고정
     */
    @Test
    fun `register allowed on activation day and last active day, blocked after`() {
        val activation = LocalDate.now().minusDays(41)
        val lastActive = activation.plusDays(41) // today

        // D0 허용
        val day0 = DailyAssessmentRequest(
            prescriptionCode = "WERT2588",
            date = activation,
            painScore = 5, stressScore = 5, functionScore = 5,
            pains = emptyList()
        )
        mockMvc.perform(
            post("/api/v1/assessments/daily").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(day0))
        ).andExpect(status().isCreated)

        // D+41 허용 
        val day41 = day0.copy(date = lastActive)
        mockMvc.perform(
            post("/api/v1/assessments/daily").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(day41))
        ).andExpect(status().isCreated)

        // D+42 차단 (활성 기간 초과 + 미래 날짜)
        val day42 = day0.copy(date = lastActive.plusDays(1))
        mockMvc.perform(
            post("/api/v1/assessments/daily").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(day42))
        ).andExpect(status().isBadRequest)
    }

    /**
     * 변화율 계산 특수 케이스: 이전 주 평균이 0인 경우
     * - 분모 0 → NaN/Infinity 방지, 0.0으로 처리되는지 확인
     */
    @Test
    fun `weekly trend change rate prev zero yields 0 instead of NaN or Inf`() {
        // week1 평균 0으로 시딩
        val base = LocalDate.now().minusDays(10)
        val w1d1 = DailyAssessmentRequest(
            prescriptionCode = "QWER2348",
            date = base,
            painScore = 0, stressScore = 0, functionScore = 0,
            pains = emptyList()
        )
        mockMvc.perform(post("/api/v1/assessments/daily").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(w1d1))).andExpect(status().isCreated)

        // week2는 0이 아닌 값 → 변화율 계산에서 분모0 방어 로직 확인
        val w2d1 = w1d1.copy(date = base.plusDays(7), painScore = 5, stressScore = 5, functionScore = 5)
        mockMvc.perform(post("/api/v1/assessments/daily").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(w2d1))).andExpect(status().isCreated)

        mockMvc.perform(get("/api/v1/assessments/weekly-trend").param("prescriptionCode", "QWER2348"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.weeklyTrend[1].changeRate.pain").value(0.0))
            .andExpect(jsonPath("$.weeklyTrend[1].changeRate.stress").value(0.0))
            .andExpect(jsonPath("$.weeklyTrend[1].changeRate.function").value(0.0))
    }

    /**
     * 검증 실패: pains 요소 수가 6 초과 → 400
     */
    @Test
    fun `reject pains over six`() {
        val pains = (1..7).map { _ -> PainInput(location = PainRegion.LEFT_JAW, intensity = 1, note = null) }
        val body = DailyAssessmentRequest(
            prescriptionCode = "ABCD1234",
            date = LocalDate.now().minusDays(2),
            painScore = 1, stressScore = 1, functionScore = 1,
            pains = pains
        )
        mockMvc.perform(post("/api/v1/assessments/daily").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(body))).andExpect(status().isBadRequest)
    }

    /**
     * 검증 실패: pains 내 위치 중복 금지 → 400
     * - 동일 location을 두 번 전달하면 거부
     */
    @Test
    fun `reject duplicate pain locations`() {
        val body = DailyAssessmentRequest(
            prescriptionCode = "ABCD1234",
            date = LocalDate.now().minusDays(2),
            painScore = 2, stressScore = 2, functionScore = 2,
            pains = listOf(
                PainInput(PainRegion.LEFT_JAW, 3, null),
                PainInput(PainRegion.LEFT_JAW, 4, null)
            )
        )
        mockMvc.perform(post("/api/v1/assessments/daily").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(body))).andExpect(status().isBadRequest)
    }

    /**
     * 검증 실패: 미래 날짜 차단 → 400
     */
    @Test
    fun `reject future date`() {
        val body = DailyAssessmentRequest(
            prescriptionCode = "ABCD1234",
            date = LocalDate.now().plusDays(1),
            painScore = 2, stressScore = 2, functionScore = 2,
            pains = emptyList()
        )
        mockMvc.perform(post("/api/v1/assessments/daily").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(body))).andExpect(status().isBadRequest)
    }

    /**
    * 주차별 트렌드 API 기본 호출 성공 케이스
    *
    * - 필수 파라미터(prescriptionCode)만 전달했을 때 200 OK 응답 확인
    * - 응답 JSON에 prescriptionCode 필드가 포함되는지 최소 필드 검증
    * - 주차별 데이터 내용(week grouping, changeRate 등)은 본 테스트에서 검증하지 않음
     */
    @Test
    fun `weekly trend returns grouped weeks`() {
        mockMvc.perform(
            get("/api/v1/assessments/weekly-trend")
                .param("prescriptionCode", "ABCD1234")
        ).andExpect(status().isOk)
         .andExpect(jsonPath("$.prescriptionCode").value("ABCD1234"))
    }

    /**
     * 검증 실패: startWeek 범위(1..6) 미준수 → 400
     */
    @Test
    fun `weekly trend rejects invalid week range`() {
        mockMvc.perform(
            get("/api/v1/assessments/weekly-trend")
                .param("prescriptionCode", "ABCD1234")
                .param("startWeek", "0")
        ).andExpect(status().isBadRequest)
    }

    /**
     * 검증 실패: 잘못된 처방코드 패턴(예: INVALID1) → 400
     * - Controller에서 파라미터 바인딩 시 또는 Service에서 형식 검증 실패 시 ProblemDetail 처리
     */
    @Test
    fun `weekly trend rejects invalid code pattern`() {
        mockMvc.perform(
            get("/api/v1/assessments/weekly-trend")
                .param("prescriptionCode", "INVALID1")
        ).andExpect(status().isBadRequest)
    }

    /**
     * 검증 실패: DailyAssessmentRequest.prescriptionCode @Pattern 위반 → 400
     * - 길이/대문자/숫자 요건 불충족 케이스 모음
     */
    @Test
    fun `create daily rejects invalid prescription code pattern`() {
        val invalidCodes = listOf("ABCDE123", "ABCD123", "abcd1234", "ABCD12345")
        invalidCodes.forEach { code ->
            val body = DailyAssessmentRequest(
                prescriptionCode = code,
                date = LocalDate.now().minusDays(1),
                painScore = 1, stressScore = 1, functionScore = 1,
                pains = emptyList()
            )
            mockMvc.perform(post("/api/v1/assessments/daily").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(body))).andExpect(status().isBadRequest)
        }
    }

    /**
     * startWeek/endWeek 기본값 적용 확인
     * - 파라미터 생략 시 서비스 로직에서 기본값 적용되어 정상 응답
     */
    @Test
    fun `weekly trend applies null defaults for start and end weeks`() {
        mockMvc.perform(
            get("/api/v1/assessments/weekly-trend")
                .param("prescriptionCode", "ABCD1234")
        ).andExpect(status().isOk)
         .andExpect(jsonPath("$.prescriptionCode").value("ABCD1234"))
    }
}
