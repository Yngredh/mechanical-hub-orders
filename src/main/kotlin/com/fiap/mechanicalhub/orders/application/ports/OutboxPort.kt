package com.fiap.mechanicalhub.orders.application.ports

import com.fiap.mechanicalhub.orders.application.events.OutgoingEvent

/** Grava o evento em `outbox_events`; deve participar da transacao do caso de uso. */
interface OutboxPort {
    fun append(event: OutgoingEvent)
}
