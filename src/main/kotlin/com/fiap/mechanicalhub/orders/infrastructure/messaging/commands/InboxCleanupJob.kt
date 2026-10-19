package com.fiap.mechanicalhub.orders.infrastructure.messaging.commands

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration

/**
 * Job diario (failure_handling.md §7): apaga `inbox_events` com mais de 30
 * dias, prazo maior que a retencao da DLQ (14 dias) para que reprocessamentos
 * ainda sejam deduplicados. Idempotente: varias instancias nao conflitam.
 */
@Component
class InboxCleanupJob(
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
) {
    @Scheduled(cron = "\${mechanical-hub.orders.inbox.cleanup-cron:0 0 3 * * *}", zone = "UTC")
    fun purgeExpired(): Int {
        val limit = clock.instant().minus(RETENTION)
        val deleted = jdbcTemplate.update("DELETE FROM inbox_events WHERE processed_at < ?", Timestamp.from(limit))
        log.info("Limpeza do inbox_events: {} registro(s) anteriores a {} apagados", deleted, limit)
        return deleted
    }

    private companion object {
        val RETENTION: Duration = Duration.ofDays(30)
        val log = LoggerFactory.getLogger(InboxCleanupJob::class.java)
    }
}
