package com.fiap.mechanicalhub.orders.infrastructure.persistence.adapters

import com.fiap.mechanicalhub.orders.application.events.OutgoingEvent
import com.fiap.mechanicalhub.orders.application.ports.OutboxPort
import com.fiap.mechanicalhub.orders.infrastructure.messaging.MessageEnvelope
import com.fiap.mechanicalhub.orders.infrastructure.persistence.entities.OutboxEventEntity
import com.fiap.mechanicalhub.orders.infrastructure.persistence.repositories.OutboxEventJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.util.UUID

/** Grava o envelope em `outbox_events` na transacao ja aberta pelo caso de uso. */
@Component
class OutboxAdapter(
    private val outboxRepository: OutboxEventJpaRepository,
    private val jsonMapper: JsonMapper,
    private val clock: Clock,
) : OutboxPort {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun append(event: OutgoingEvent) {
        val eventId = UUID.randomUUID()
        val now = clock.instant()
        outboxRepository.save(
            OutboxEventEntity(
                eventId = eventId,
                aggregateId = event.orderId,
                eventType = event.type,
                payload = jsonMapper.writeValueAsString(MessageEnvelope.of(event, eventId, now)),
                createdAt = now,
            ),
        )
    }
}
