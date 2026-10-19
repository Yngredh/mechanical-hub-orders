package com.fiap.mechanicalhub.orders.application.events

import java.util.UUID

/**
 * Eventos publicados para o orquestrador (sempre via outbox, na transacao do
 * caso de uso). `type` e o nome do contrato na fila.
 */
sealed interface OutgoingEvent {
    val type: String
    val orderId: UUID
    val transactionId: UUID?

    data class DiagnosisCompleted(
        override val orderId: UUID,
        val repairs: List<RepairReference>,
    ) : OutgoingEvent {
        override val type = "diagnosisCompleted"
        override val transactionId: UUID? = null
    }

    data class OrderApproved(
        override val transactionId: UUID,
        override val orderId: UUID,
    ) : OutgoingEvent {
        override val type = "orderApproved"
    }

    data class OrderRejected(
        override val transactionId: UUID,
        override val orderId: UUID,
    ) : OutgoingEvent {
        override val type = "orderRejected"
    }

    data class OrderFinished(
        override val transactionId: UUID,
        override val orderId: UUID,
    ) : OutgoingEvent {
        override val type = "orderFinished"
    }

    data class OrderCanceled(
        override val transactionId: UUID,
        override val orderId: UUID,
    ) : OutgoingEvent {
        override val type = "orderCanceled"
    }
}

data class RepairReference(
    val repairId: UUID,
)
