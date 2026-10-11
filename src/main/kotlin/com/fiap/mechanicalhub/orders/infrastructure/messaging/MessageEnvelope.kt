package com.fiap.mechanicalhub.orders.infrastructure.messaging

import com.fiap.mechanicalhub.orders.application.events.OutgoingEvent
import java.time.Instant
import java.util.UUID

/**
 * Envelope JSON das mensagens (infrastructure.md §4): `eventId`,
 * `transactionId`, `orderId`, `type`, `version`, `timestamp` + os campos do
 * payload no mesmo nivel. O contexto de trace vai nos message attributes.
 */
object MessageEnvelope {
    const val CONTRACT_VERSION = 1

    fun of(
        event: OutgoingEvent,
        eventId: UUID,
        timestamp: Instant,
    ): Map<String, Any?> =
        linkedMapOf(
            "eventId" to eventId,
            "transactionId" to event.transactionId,
            "orderId" to event.orderId,
            "type" to event.type,
            "version" to CONTRACT_VERSION,
            "timestamp" to timestamp.toString(),
        ) + payloadOf(event)

    private fun payloadOf(event: OutgoingEvent): Map<String, Any?> =
        when (event) {
            is OutgoingEvent.DiagnosisCompleted -> mapOf("repairs" to event.repairs.map { mapOf("repairId" to it.repairId) })

            is OutgoingEvent.OrderApproved,
            is OutgoingEvent.OrderRejected,
            is OutgoingEvent.OrderFinished,
            is OutgoingEvent.OrderCanceled,
            -> emptyMap()
        }
}
