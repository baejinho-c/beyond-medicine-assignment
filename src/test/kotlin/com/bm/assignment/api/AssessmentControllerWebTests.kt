package com.bm.assignment.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import com.bm.assignment.service.AssessmentService

/**
 * AssessmentControllerWebTests
 *
 * - 컨트롤러 계층(@RestController)의 요청/응답을 단위 수준에서 검증한다.
 * - 실제 서비스 로직은 MockBean으로 대체하여,
 *   HTTP 요청 처리, Validation, ProblemDetail 포맷 등 Web 계층 로직만 집중적으로 테스트한다.
 */
@WebMvcTest(controllers = [AssessmentController::class])
class AssessmentControllerWebTests(@Autowired val mockMvc: MockMvc) {

    // 비즈니스 로직(Service 계층)을 Mock 처리
    // → 컨트롤러만 독립적으로 테스트 가능
    @MockBean
    lateinit var service: AssessmentService

    /**
     * 케이스 1: 유효하지 않은 처방 코드 전달 시
     *
     * - weekly-trend API 호출 시 잘못된 prescriptionCode를 전달하면
     * - ControllerAdvice(GlobalExceptionHandler)에 의해 RFC7807 ProblemDetail(JSON) 형식으로
     *   400 Bad Request가 반환되어야 함을 검증한다.
     */
    @Test
    fun `invalid prescription code yields problem detail 400`() {
        mockMvc.perform(
            get("/api/v1/assessments/weekly-trend")
                .param("prescriptionCode", "INVALID1")
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().contentType("application/problem+json"))
    }

    /**
     * 케이스 2: 잘못된 주차(startWeek=0) 전달 시
     *
     * - startWeek가 1보다 작은 경우 Validation 예외가 발생해야 함.
     * - 응답은 RFC7807 ProblemDetail(JSON) 포맷의 400 응답이어야 한다.
     */
    @Test
    fun `invalid weeks yield problem detail 400`() {
        mockMvc.perform(
            get("/api/v1/assessments/weekly-trend")
                .param("prescriptionCode", "ABCD1234")
                .param("startWeek", "0")
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().contentType("application/problem+json"))
    }
}
