# Beyond Medicine Backend Assignment (Kotlin / Spring Boot)

이 프로젝트는 비욘드 메디슨(Beyond Medicine) 의 턱관절 질환 디지털 치료제 서비스를 위한 백엔드 과제입니다.
Kotlin + Spring Boot 환경에서 처방(Prescription) 과 일일 평가(Daily Assessment) 를 관리하며,
통증·스트레스·기능 점수를 기반으로 한 주차별 추세(Weekly Trend) 분석 기능을 제공합니다.

본 애플리케이션은 완전히 독립 실행 가능한 형태이며, H2 In-Memory Database 를 사용해
로컬 환경에서도 즉시 실행 및 테스트가 가능합니다.


---

## 1. Tech Stack

- Kotlin 1.9  
- Spring Boot 3.3 (Web, JPA, Validation)  
- H2 (in-memory)  
- springdoc-openapi (Swagger UI)  
- JUnit 5, MockMvc (통합 테스트)

---

## 2. 로컬 실행 방법

```bash
./gradlew bootRun
# 또는
./gradlew test
```

- **Swagger UI:** http://localhost:8080/swagger-ui/index.html  
- **H2 Console:** http://localhost:8080/h2-console  
  - JDBC URL: `jdbc:h2:mem:bmdb`  
  - Username: `sa`

애플리케이션 실행 시 `DataInit` 설정에 따라 샘플 처방 데이터 6건이 자동 생성

---

## 3. Domain 구조

### Prescription
- 처방 코드, 생성일, 활성화일, 상태(WAITING, ACTIVE, COMPLETED, EXPIRED)
- 상태 계산 로직 포함 (활성화일 기준 6주 단위)

### DailyAssessment
- 일일 통증 점수, 스트레스 점수, 기능 점수  
- 통증 부위 리스트 포함 (`AssessmentPain`과 1:N 관계)

### AssessmentPain
- 통증 부위(`location`), 강도(`intensity`), 메모(`note`)  
- `DailyAssessment`에 종속

### PainRegion
- LEFT_JAW, RIGHT_JAW, LEFT_TEMPLE, RIGHT_TEMPLE, NECK, CHIN

### PrescriptionStatus
- WAITING, ACTIVE, COMPLETED, EXPIRED

---

## 4. API 요약

### POST `/api/v1/assessments/daily`
- 일일 평가 등록

### GET `/api/v1/assessments/weekly-trend`
- 주차별 평균 변화율 및 주요 통증 부위 통계 조회

---

## 5. POST `/api/v1/assessments/daily`

### 입력 제약사항

- `prescriptionCode`: 8자, 대문자 4개 + 숫자 4개 포함 (순서 무관)  
- `painScore`, `stressScore`, `functionScore`: 0~10 정수  
- `pains`: 0~6개, 통증 부위 중복 불가, intensity 0~10  
- 활성화 기간(`D0`~`D+41`) 내에서만 등록 가능  
- 하루 1회 제한 (중복 시 409 Conflict)

### Request 예시

```json
{
  "prescriptionCode": "ABCD1234",
  "date": "2025-09-29",
  "painScore": 7,
  "stressScore": 5,
  "functionScore": 6,
  "pains": [
    { "location": "LEFT_JAW", "intensity": 8, "note": "chewing pain" },
    { "location": "RIGHT_TEMPLE", "intensity": 6 }
  ]
}
```

### Response 예시

```json
{
  "assessmentId": 1,
  "prescriptionCode": "ABCD1234",
  "week": 3
}
```

### Error 예시 (RFC7807)

```json
{
  "type": "https://docs.beyondmed.example.com/problems/validation-error",
  "title": "유효성 검사 실패",
  "status": 400,
  "detail": "pains[0].intensity: must be <= 10"
}
```

---

## 6. GET `/api/v1/assessments/weekly-trend`

### 파라미터

- `prescriptionCode`: 필수, 8자 (4문자 + 4숫자)  
- `startWeek`: 선택, 1~6 (기본값 1)  
- `endWeek`: 선택, 1~6 (기본값: 현재 주차)

### Response 예시

```json
{
  "prescriptionCode": "ABCD1234",
  "period": { "startWeek": 1, "endWeek": 4 },
  "weeklyTrend": [
    {
      "week": 1,
      "avgPain": 7.0,
      "avgStress": 5.0,
      "avgFunction": 6.0,
      "changeRate": null
    },
    {
      "week": 2,
      "avgPain": 6.0,
      "avgStress": 4.0,
      "avgFunction": 5.5,
      "changeRate": { "pain": 14.3, "stress": 20.0, "function": -8.3 }
    }
  ],
  "topPainLocations": [
    { "location": "LEFT_JAW", "count": 3, "avgIntensity": 7.3 },
    { "location": "RIGHT_TEMPLE", "count": 2, "avgIntensity": 6.0 }
  ]
}
```

### 주차 계산

```
week = floor((date - activatedDate).days / 7) + 1   // 1~6 범위
```

### 활성 기간

```
ACTIVE: D0 ~ D+41 (6주)
COMPLETED: D+42 이후
```

---

## 7. 설계 포인트

### Validation
- DTO에서 `@Pattern`, `@Min`, `@Max`, `@Size` 로 입력 형식 검증  
- `@Validated` 로 QueryParam 검증 활성화

### Error Handling
- RFC7807 표준(`ProblemDetail`)을 사용  
- 예외별 title/type 을 명시적으로 관리하여 문서화 용이

### Time Handling
- `Clock(Asia/Seoul)` 주입  
- 서버 시간대와 무관하게 KST 기준 주차 계산 보장

### Business Rules
- 하루 1회 제한  
- 활성 기간 내 등록만 허용  
- 통증 부위 중복 금지

### Repository
- `findRangeWithPains()` 에서 JPQL + fetch join 사용 → N+1 최소화

### API Docs
- springdoc-openapi 기반 Swagger UI 자동 생성  
- 응답 구조 및 상태 코드 명세 포함

---

## 8. Testing

```bash
./gradlew test
```

테스트는 MockMvc 기반 통합 테스트 형태로 구성되어 있으며, 주요 시나리오를 모두 포함합니다.
<img width="720" height="365" alt="스크린샷 2025-10-17 오후 5 08 29" src="https://github.com/user-attachments/assets/700b49ac-7673-499d-bed3-6b9fe94ce7ab" />



### 주요 테스트 항목
- Daily Assessment 정상 등록 및 중복 등록(409)  
- 활성 기간 경계 (D0, D+41 허용 / D+42 차단)  
- 통증 부위 6개 초과 또는 중복 방지  
- 미래 날짜 입력 차단  
- 코드 형식 위반 시 400 반환  
- 주차별 트렌드 평균 및 변화율 계산 검증

---

## 9. Architectural Decisions (ADR)

### ADR-001: KST Clock 주입
운영 서버의 시간대(UTC 등)와 관계없이 KST 기준으로 일관된 주차 계산을 위해  
`Clock(Asia/Seoul)`을 주입함.

### ADR-002: RFC7807 도입
에러 응답을 표준화하고 클라이언트/문서화 호환성을 높이기 위해  
`ProblemDetail` 구조를 사용함.

### ADR-003: Validation Layer 분리
DTO에서 형식 검증을 처리하고, Service 계층에서는 비즈니스 규칙만 검증하도록  
역할을 분리함.

### ADR-004: 테스트 중심 설계
요구사항별 정상 및 에러 케이스를 테스트 코드로 명세화하여  
회귀 안정성을 확보함.

---

## 10. Review Notes

- Validation 과 Business Rule 계층이 명확히 분리됨  
- 에러 응답이 표준화되어 클라이언트 연동이 용이함  
- 주요 정상/에러 시나리오가 테스트 코드로 보장됨  
- Controller, Service, Repository 가 명확히 분리되어 유지보수가 용이함  
- 실무에서도 바로 배포 가능한 구조 수준

---

## 11. Example Use Cases

- 보호자가 매일 통증 정도를 기록하고 주차별 변화를 확인  
- 임상 담당자가 주차별 평균 통증·스트레스 변화를 모니터링  
- 상위 통증 부위를 분석해 디지털 치료 모델 개선에 활용

---

## 12. 추가 제안 사항
 
- 사용자 일일 평가 지연 입력	사용자가 하루 평가를 놓치거나 자정 이후 제출하는 경우, 현 로직에서는 등록이 거부됨. 
실제 임상 환경에서는 지연 입력이 빈번하므로 “지연 허용 모드” 또는 “보정 로직(입력일 ≠ 평가일)” 고려 필요.


