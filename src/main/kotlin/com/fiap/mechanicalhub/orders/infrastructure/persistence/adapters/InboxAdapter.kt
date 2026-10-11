package com.fiap.mechanicalhub.orders.infrastructure.persistence.adapters

import com.fiap.mechanicalhub.orders.application.events.OutgoingEvent
import com.fiap.mechanicalhub.orders.application.ports.ConsumerTransaction
import com.fiap.mechanicalhub.orders.application.ports.InboxPort
import com.fiap.mechanicalhub.orders.application.ports.ProcessedCommand
import com.fiap.mechanicalhub.orders.infrastructure.messaging.OutgoingEventCodec
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Clock

@Component
class InboxAdapter(
    private val jdbcTemplate: JdbcTemplate,
    private val codec: OutgoingEventCodec,
    private val clock: Clock,
) : InboxPort {
    override fun find(messageKey: String): ProcessedCommand? =
        jdbcTemplate
            .query(
                "SELECT response_payload::text AS response FROM inbox_events WHERE message_key = ?",
                { rs, _ -> ProcessedCommand(rs.getString("response")?.let(codec::decode)) },
                messageKey,
            ).firstOrNull()

    /** `message_key` e PK: uma corrida entre duas entregas falha aqui e e retentada. */
    override fun register(
        messageKey: String,
        response: OutgoingEvent?,
    ) {
        jdbcTemplate.update(
            "INSERT INTO inbox_events (message_key, response_payload, processed_at) VALUES (?, ?::jsonb, ?)",
            messageKey,
            response?.let(codec::encode),
            Timestamp.from(clock.instant()),
        )
    }
}

@Component
class JdbcConsumerTransaction(
    private val jdbcTemplate: JdbcTemplate,
) : ConsumerTransaction {
    override fun applyLockTimeout() {
        jdbcTemplate.execute("SET LOCAL lock_timeout = '5s'")
    }
}
