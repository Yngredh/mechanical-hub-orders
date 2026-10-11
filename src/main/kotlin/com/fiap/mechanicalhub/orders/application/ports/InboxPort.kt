package com.fiap.mechanicalhub.orders.application.ports

import com.fiap.mechanicalhub.orders.application.events.OutgoingEvent

/** Comando ja processado; `response` e o evento publicado na primeira vez (se houve). */
data class ProcessedCommand(
    val response: OutgoingEvent?,
)

/** `inbox_events`: deduplicacao dos comandos por `idempotencyKey`. */
interface InboxPort {
    fun find(messageKey: String): ProcessedCommand?

    fun register(
        messageKey: String,
        response: OutgoingEvent?,
    )
}

/** Ajustes da transacao do consumidor. */
fun interface ConsumerTransaction {
    /** `SET LOCAL lock_timeout`: espera por lock vira erro transitorio retentavel. */
    fun applyLockTimeout()
}
