package com.fiap.mechanicalhub.orders.infrastructure.observability

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * `outbox_oldest_pending_age_seconds`: idade da linha `PENDING` mais antiga do
 * outbox (0 sem pendencias). Alerta quando passa de 60 s (failure_handling.md §4).
 */
@Component
class OutboxMetrics(
    private val jdbcTemplate: JdbcTemplate,
    meterRegistry: MeterRegistry,
) {
    init {
        Gauge
            .builder("outbox.oldest.pending.age", this) { it.oldestPendingAgeSeconds() }
            .baseUnit("seconds")
            .description("Idade da mensagem PENDING mais antiga do outbox_events")
            .register(meterRegistry)
    }

    fun oldestPendingAgeSeconds(): Double =
        runCatching {
            jdbcTemplate.queryForObject(
                "SELECT COALESCE(EXTRACT(EPOCH FROM now() - min(created_at)), 0) FROM outbox_events WHERE status = 'PENDING'",
                Double::class.java,
            ) ?: 0.0
        }.onFailure { log.warn("Nao foi possivel medir o outbox: {}", it.message) }
            .getOrDefault(Double.NaN)

    private companion object {
        val log = LoggerFactory.getLogger(OutboxMetrics::class.java)
    }
}
