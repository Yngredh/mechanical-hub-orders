package com.fiap.mechanicalhub.orders.infrastructure.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.SecureRandom
import java.time.Clock

@Configuration
class ApplicationConfig {
    /** Data e hora injetadas: os testes substituem por um Clock fixo. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun secureRandom(): SecureRandom = SecureRandom()

    @Bean
    fun openApi(): OpenAPI =
        OpenAPI().info(
            Info()
                .title("Mechanical Hub - Orders")
                .description(
                    "Ordens de servico: API do mecanico (identidade via x-user-id/x-user-role/x-user-name, " +
                        "repassados pelo API Gateway) e API publica do cliente (header X-Approval-Token).",
                ).version("v1"),
        )
}
