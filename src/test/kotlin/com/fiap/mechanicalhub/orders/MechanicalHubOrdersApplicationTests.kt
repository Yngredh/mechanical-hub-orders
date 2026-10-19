package com.fiap.mechanicalhub.orders

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class MechanicalHubOrdersApplicationTests {
    @Test
    fun `given the application when the context starts then it loads`() {
        // Falha se o contexto Spring nao subir.
    }
}
