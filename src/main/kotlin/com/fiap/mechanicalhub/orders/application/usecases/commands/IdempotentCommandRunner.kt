package com.fiap.mechanicalhub.orders.application.usecases.commands

import com.fiap.mechanicalhub.orders.application.events.OutgoingEvent
import com.fiap.mechanicalhub.orders.application.notifications.Notification
import com.fiap.mechanicalhub.orders.application.ports.ConsumerTransaction
import com.fiap.mechanicalhub.orders.application.ports.InboxPort
import com.fiap.mechanicalhub.orders.application.ports.OutboxPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** Efeito de um comando: resposta para o outbox e notificacao para depois do COMMIT. */
data class CommandEffect(
    val response: OutgoingEvent? = null,
    val notification: Notification? = null,
)

sealed interface CommandOutcome {
    data class Applied(
        val notification: Notification?,
    ) : CommandOutcome

    /** Ja processado: nada reaplicado (a resposta original, se houve, e regravada no outbox). */
    data object Duplicate : CommandOutcome
}

/**
 * Consumo idempotente (TD_finishedOS.md §0.3), na transacao do caso de uso:
 * deduplicacao em `inbox_events` → regra → `inbox_events` → `outbox_events`.
 */
@Component
class IdempotentCommandRunner(
    private val inbox: InboxPort,
    private val outbox: OutboxPort,
    private val consumerTransaction: ConsumerTransaction,
) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun run(
        idempotencyKey: String,
        handler: () -> CommandEffect,
    ): CommandOutcome {
        consumerTransaction.applyLockTimeout()

        val processed = inbox.find(idempotencyKey)
        if (processed != null) {
            processed.response?.let(outbox::append)
            return CommandOutcome.Duplicate
        }

        val effect = handler()
        inbox.register(idempotencyKey, effect.response)
        effect.response?.let(outbox::append)
        return CommandOutcome.Applied(effect.notification)
    }
}
