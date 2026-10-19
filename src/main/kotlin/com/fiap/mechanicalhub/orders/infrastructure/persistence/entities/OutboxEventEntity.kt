package com.fiap.mechanicalhub.orders.infrastructure.persistence.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

enum class OutboxStatus { PENDING, SENT }

@Entity
@Table(name = "outbox_events")
class OutboxEventEntity(
    @Column(name = "event_id", nullable = false, updatable = false)
    var eventId: UUID,
    /** orderId: MessageGroupId da fila FIFO. */
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    var aggregateId: UUID,
    @Column(name = "event_type", nullable = false, updatable = false)
    var eventType: String,
    /** Envelope JSON completo, enviado como corpo da mensagem SQS. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false)
    var payload: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,
) {
    /** BIGSERIAL: ordem de gravacao e de publicacao. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sequence")
    var sequence: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OutboxStatus = OutboxStatus.PENDING

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0

    @Column(name = "last_error")
    var lastError: String? = null

    @Column(name = "sent_at")
    var sentAt: Instant? = null
}
