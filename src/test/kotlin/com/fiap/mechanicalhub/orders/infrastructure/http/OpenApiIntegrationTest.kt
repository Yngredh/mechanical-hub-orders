package com.fiap.mechanicalhub.orders.infrastructure.http

import com.fiap.mechanicalhub.orders.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get

class OpenApiIntegrationTest : IntegrationTest() {
    @Test
    fun `given springdoc when reading the api docs then the order routes are documented`() {
        val result = mockMvc.get("/v3/api-docs")

        result.andExpect {
            status { isOk() }
            jsonPath("$.paths['/orders/{orderId}/status']") { exists() }
            jsonPath("$.paths['/mechanical-hub/service-orders/{orderId}/approve']") { exists() }
        }
    }
}
