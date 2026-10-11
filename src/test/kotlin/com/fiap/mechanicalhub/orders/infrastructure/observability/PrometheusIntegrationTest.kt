package com.fiap.mechanicalhub.orders.infrastructure.observability

import com.fiap.mechanicalhub.orders.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.get
import java.util.UUID
import kotlin.test.assertTrue

@AutoConfigureMetrics
// Sem relay: a linha PENDING inserida no teste nao e publicada antes da medicao.
@TestPropertySource(properties = ["mechanical-hub.orders.outbox.relay-enabled=false"])
class PrometheusIntegrationTest : IntegrationTest() {
    @Autowired
    private lateinit var outboxMetrics: OutboxMetrics

    @Test
    fun `given the actuator when scraping prometheus then the outbox age gauge is exposed`() {
        val result = mockMvc.get("/actuator/prometheus")

        result.andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("outbox_oldest_pending_age_seconds")) }
        }
    }

    @Test
    fun `given an old pending event when measuring then the age reflects it`() {
        jdbcTemplate.update(
            """
            INSERT INTO outbox_events (event_id, aggregate_id, event_type, payload, status, created_at)
            VALUES (?, ?, 'orderApproved', '{}'::jsonb, 'PENDING', now() - interval '2 minutes')
            """.trimIndent(),
            UUID.randomUUID(),
            UUID.randomUUID(),
        )

        val age = outboxMetrics.oldestPendingAgeSeconds()

        assertTrue(age >= TWO_MINUTES, "idade inesperada: $age")
    }

    private companion object {
        const val TWO_MINUTES = 120.0
    }
}
