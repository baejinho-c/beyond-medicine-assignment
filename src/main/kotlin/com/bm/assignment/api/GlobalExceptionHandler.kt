package com.bm.assignment.api

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.dao.DataIntegrityViolationException
import jakarta.validation.ConstraintViolationException
import com.bm.assignment.api.exception.DuplicateDailyAssessmentException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.net.URI

/**
 * GlobalExceptionHandler
 *
 * 프로젝트 전역에서 발생하는 예외를 처리하고,
 * RFC 7807 형식(ProblemDetail)으로 일관된 에러 응답을 반환합니다.
 *
 * 각 예외 유형별로 상태 코드, 메시지, type(문서 링크) 등을 지정하며
 * 프론트엔드 및 외부 API 클라이언트에서 동일한 구조로 파싱할 수 있도록 한다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    /**
     * @throws MethodArgumentNotValidException
     * 유효성 검증(@Valid) 실패 시 발생.
     *
     * BindingResult 내 모든 필드 에러를 수집하여 세부 메시지를 구성하고,
     * RFC 7807 포맷으로 400 응답을 반환한다.
     *
     * 예시 응답:
     * {
     *   "type": "https://docs.beyondmed.example.com/problems/validation-error",
     *   "title": "유효성 검사 실패",
     *   "status": 400,
     *   "detail": "painScore: must be <= 10; stressScore: must be >= 0",
     *   "errors": [
     *     { "field": "painScore", "message": "must be <= 10" },
     *     { "field": "stressScore", "message": "must be >= 0" }
     *   ]
     * }
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ProblemDetail> {
        val fieldErrors = ex.bindingResult.fieldErrors
        val detail = fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }

        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail)
        pd.title = "유효성 검사 실패"
        pd.type = URI.create("https://docs.beyondmed.example.com/problems/validation-error")
        pd.setProperty("errors", fieldErrors.map {
            mapOf("field" to it.field, "message" to (it.defaultMessage ?: "invalid"))
        })

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd)
    }

    /**
     * @throws ConstraintViolationException
     * Bean Validation 제약 조건 위반 시 발생.
     *
     * @Valid 외부에서 발생하는 제약조건 예외에 대응하며,
     * 간단한 메시지만 반환한다.
     */
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ProblemDetail> {
        val detail = ex.constraintViolations.firstOrNull()?.message ?: "validation error"

        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail)
        pd.title = "잘못된 요청"
        pd.type = URI.create("https://docs.beyondmed.example.com/problems/bad-request")

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd)
    }

    /**
     * @throws DuplicateDailyAssessmentException
     * 동일한 날짜/처방 코드로 평가가 중복 등록될 경우 발생.
     *
     * 중복 충돌로 간주하여 409 Conflict 응답을 반환한다.
     */
    @ExceptionHandler(DuplicateDailyAssessmentException::class)
    fun handleDuplicate(ex: DuplicateDailyAssessmentException): ResponseEntity<ProblemDetail> {
        val pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            ex.message ?: "duplicate daily assessment for date"
        )
        pd.title = "중복 충돌"
        pd.type = URI.create("https://docs.beyondmed.example.com/problems/duplicate-daily-assessment")

        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd)
    }

    /**
     * @throws IllegalArgumentException, IllegalStateException
     * 일반적인 잘못된 요청 또는 상태 오류에 대한 처리.
     *
     * 비즈니스 로직 검증 중 발생하는 IllegalArgument/State 예외를 포괄 처리하며,
     * 주로 400 Bad Request를 반환한다.
     */
    @ExceptionHandler(IllegalArgumentException::class, IllegalStateException::class)
    fun handleBadRequest(ex: RuntimeException): ResponseEntity<ProblemDetail> {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "bad request")
        pd.title = "잘못된 요청"
        pd.type = URI.create("https://docs.beyondmed.example.com/problems/bad-request")

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd)
    }

    /**
     * JSON 파싱 실패 또는 Enum/형식 불일치 등 본문 역직렬화 오류 처리
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<ProblemDetail> {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed JSON or invalid value")
        pd.title = "잘못된 요청 본문"
        pd.type = URI.create("https://docs.beyondmed.example.com/problems/bad-request")
        pd.setProperty("cause", ex.mostSpecificCause?.message ?: ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd)
    }

    /**
     * 파라미터 타입 변환 실패 처리 (e.g., 쿼리 파라미터 타입 불일치)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ProblemDetail> {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid parameter type: ${ex.name}")
        pd.title = "잘못된 요청 파라미터"
        pd.type = URI.create("https://docs.beyondmed.example.com/problems/bad-request")
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd)
    }

    /**
     * @throws DataIntegrityViolationException
     * DB 제약조건(Unique/ForeignKey 등) 위반 시 발생.
     *
     * DuplicateDailyAssessmentException과 유사하지만,
     * DB 계층에서 직접 발생하는 예외를 별도로 처리한다.
     */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(ex: DataIntegrityViolationException): ResponseEntity<ProblemDetail> {
        val pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "duplicate daily assessment for date"
        )
        pd.title = "중복 충돌"
        pd.type = URI.create("https://docs.beyondmed.example.com/problems/data-integrity-violation")

        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd)
    }
}
