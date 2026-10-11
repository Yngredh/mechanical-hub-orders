package com.fiap.mechanicalhub.orders.infrastructure.messaging.outbox

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Clock

@Component
class JdbcOutboxStore(
    private val jdbcTemplate: JdbcTemplate,
) : OutboxStore {
    /**
     * `pg_try_advisory_xact_lock`: lock fixo do servico, liberado sozinho no fim
     * da transacao do ciclo (nao vaza para outra requisicao do pool de conexoes).
     */
    override fun tryAcquireLeadership(): Boolean =
        jdbcTemplate.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean::class.java, RELAY_LOCK_KEY) == true

    override fun findPending(limit: Int): List<PendingOutboxMessage> =
        jdbcTemplate.query(
            """
            SELECT sequence, event_id, aggregate_id, event_type, payload::text AS payload
              FROM outbox_events
             WHERE status = 'PENDING'
             ORDER BY sequence
             LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                PendingOutboxMessage(
                    sequence = rs.getLong("sequence"),
                    eventId = rs.getObject("event_id", java.util.UUID::class.java),
                    aggregateId = rs.getObject("aggregate_id", java.util.UUID::class.java),
                    eventType = rs.getString("event_type"),
                    payload = rs.getString("payload"),
                )
            },
            limit,
        )

    override fun markSent(
        sequence: Long,
        clock: Clock,
    ) {
        jdbcTemplate.update(
            "UPDATE outbox_events SET status = 'SENT', sent_at = ? WHERE sequence = ?",
            Timestamp.from(clock.instant()),
            sequence,
        )
    }

    override fun registerFailure(
        sequence: Long,
        error: String,
    ) {
        jdbcTemplate.update(
            "UPDATE outbox_events SET attempts = attempts + 1, last_error = ? WHERE sequence = ?",
            error.take(MAX_ERROR_LENGTH),
            sequence,
        )
    }

    private companion object {
        /** Chave fixa do advisory lock do relay do mechanical-hub-orders. */
        const val RELAY_LOCK_KEY = 4_100_001L
        const val MAX_ERROR_LENGTH = 2_000
    }
}
