package com.fiap.mechanicalhub.orders.infrastructure.messaging.outbox

import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.UUID

/** Linha `PENDING` do outbox pronta para envio. */
data class PendingOutboxMessage(
    val sequence: Long,
    val eventId: UUID,
    val aggregateId: UUID,
    val eventType: String,
    val payload: String,
)

/** Acesso ao `outbox_events` usado pelo relay. */
interface OutboxStore {
    /** Lideranca do ciclo: so a instancia que obtem o lock publica. */
    fun tryAcquireLeadership(): Boolean

    fun findPending(limit: Int): List<PendingOutboxMessage>

    fun markSent(
        sequence: Long,
        clock: Clock,
    )

    fun registerFailure(
        sequence: Long,
        error: String,
    )
}

/** Envio sincrono para a fila FIFO do orquestrador. */
fun interface OutboxMessagePublisher {
    fun publish(message: PendingOutboxMessage)
}

/**
 * Relay do transactional outbox (failure_handling.md §4): publica os eventos
 * `PENDING` em ordem de `sequence`, um por vez, avancando so apos a confirmacao
 * do SQS. Uma falha interrompe o lote (sem pular a linha, preservando a ordem)
 * e incrementa `attempts`; o proximo ciclo tenta de novo com o mesmo `eventId`.
 */
class OutboxRelay(
    private val store: OutboxStore,
    private val publisher: OutboxMessagePublisher,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
    private val batchSize: Int,
) {
    /** Retorna quantas mensagens foram publicadas neste ciclo. */
    fun relayBatch(): Int = transactionTemplate.execute { relayInsideTransaction() }

    private fun relayInsideTransaction(): Int {
        if (!store.tryAcquireLeadership()) return 0

        var published = 0
        for (message in store.findPending(batchSize)) {
            val failure = runCatching { publisher.publish(message) }.exceptionOrNull()
            if (failure != null) {
                log.warn("Falha ao publicar {} (eventId={}): {}", message.eventType, message.eventId, failure.message)
                store.registerFailure(message.sequence, failure.toString())
                break
            }
            store.markSent(message.sequence, clock)
            published++
        }
        return published
    }

    private companion object {
        val log = LoggerFactory.getLogger(OutboxRelay::class.java)
    }
}
