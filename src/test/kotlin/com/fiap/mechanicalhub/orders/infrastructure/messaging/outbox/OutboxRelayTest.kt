package com.fiap.mechanicalhub.orders.infrastructure.messaging.outbox

import com.fiap.mechanicalhub.orders.fixtures.FIXED_NOW
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals

class OutboxRelayTest {
    private val clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC)

    @Test
    fun `given pending messages when relaying then all are published in sequence order and marked sent`() {
        val store = FakeOutboxStore(pending = listOf(message(1), message(2), message(3)))
        val published = mutableListOf<Long>()
        val relay = relay(store) { published += it.sequence }

        relay.relayBatch()

        assertEquals(listOf(1L, 2L, 3L), published)
        assertEquals(listOf(1L, 2L, 3L), store.sent)
    }

    @Test
    fun `given a publish failure when relaying then the batch stops and attempts are incremented`() {
        val store = FakeOutboxStore(pending = listOf(message(1), message(2), message(3)))
        val relay = relay(store) { if (it.sequence == 2L) error("SQS indisponivel") }

        val published = relay.relayBatch()

        assertEquals(1, published)
        assertEquals(listOf(1L), store.sent)
        assertEquals(listOf(2L), store.failures)
    }

    @Test
    fun `given another instance holds the lock when relaying then nothing is published`() {
        val store = FakeOutboxStore(pending = listOf(message(1)), leader = false)
        val relay = relay(store) { error("nao deveria publicar") }

        val published = relay.relayBatch()

        assertEquals(0, published)
    }

    @Test
    fun `given more messages than the batch size when relaying then only one batch is read`() {
        val store = FakeOutboxStore(pending = (1L..5L).map(::message))
        val relay = relay(store, batchSize = 2) {}

        val published = relay.relayBatch()

        assertEquals(2, published)
    }

    private fun relay(
        store: OutboxStore,
        batchSize: Int = 100,
        publisher: OutboxMessagePublisher,
    ) = OutboxRelay(store, publisher, ImmediateTransactionTemplate(), clock, batchSize)

    private fun message(sequence: Long) =
        PendingOutboxMessage(sequence, UUID.randomUUID(), UUID.randomUUID(), "diagnosisCompleted", "{}")

    private class FakeOutboxStore(
        private val pending: List<PendingOutboxMessage>,
        private val leader: Boolean = true,
    ) : OutboxStore {
        val sent = mutableListOf<Long>()
        val failures = mutableListOf<Long>()

        override fun tryAcquireLeadership() = leader

        override fun findPending(limit: Int) = pending.take(limit)

        override fun markSent(
            sequence: Long,
            clock: Clock,
        ) {
            sent += sequence
        }

        override fun registerFailure(
            sequence: Long,
            error: String,
        ) {
            failures += sequence
        }
    }

    /** Executa o callback sem gerenciador de transacao real. */
    private class ImmediateTransactionTemplate : TransactionTemplate() {
        override fun <T> execute(action: TransactionCallback<T>): T = action.doInTransaction(SimpleTransactionStatus())
    }
}
