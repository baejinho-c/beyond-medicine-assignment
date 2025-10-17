package com.bm.assignment.config

import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.OpenAPI
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI(Swagger) 설정 클래스
 *
 * SpringDoc(OpenAPI 3) 기반으로 Swagger UI 문서를 자동 생성하기 위한 기본 메타데이터를 정의
 * 
 * - title: API 명칭 (Swagger 문서 상단에 표시)
 * - version: API 버전 관리 (릴리스 버전 또는 빌드 시점에 맞춰 변경)
 * - description: API의 목적 및 주요 기능 설명
 * 
 */
@Configuration
class OpenApiConfig {

    /**
     * OpenAPI 문서 설정 Bean
     *
     * SpringDoc이 이 Bean을 인식하여 Swagger UI에서 표시할 API 메타정보를 구성
     */
    @Bean
    fun customOpenAPI(): OpenAPI =
        OpenAPI().info(
            Info()
                .title("Beyond Medicine DTx API")      // 문서 제목
                .version("v1")                         // API 버전
                .description("Daily assessment & weekly trend APIs") // 간단한 기능 설명
        )
}
