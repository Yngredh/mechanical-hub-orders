package com.fiap.mechanicalhub.orders.application.usecases.commands

import com.fiap.mechanicalhub.orders.application.events.OutgoingEvent
import com.fiap.mechanicalhub.orders.fixtures.FakeInbox
import com.fiap.mechanicalhub.orders.fixtures.FakeOutbox
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class IdempotentCommandRunnerTest {
    private val inbox = FakeInbox()
    private val outbox = FakeOutbox()
    private var lockTimeoutApplied = false
    private val runner = IdempotentCommandRunner(inbox, outbox) { lockTimeoutApplied = true }
    private val canceled = OutgoingEvent.OrderCanceled(UUID.randomUUID(), UUID.randomUUID())

    @Test
    fun `given a new command when running then the effect is applied, registered and published`() {
        val outcome = runner.run("tx:CANCEL_ORDER") { CommandEffect(response = canceled) }

        assertEquals(CommandOutcome.Applied(null), outcome)
        assertEquals<Map<String, OutgoingEvent?>>(mapOf("tx:CANCEL_ORDER" to canceled), inbox.entries)
        assertEquals(listOf<OutgoingEvent>(canceled), outbox.events)
    }

    @Test
    fun `given a processed command with response when running again then only the response is republished`() {
        inbox.entries["tx:CANCEL_ORDER"] = canceled
        var handlerCalls = 0

        val outcome =
            runner.run("tx:CANCEL_ORDER") {
                handlerCalls++
                CommandEffect()
            }

        assertEquals<Triple<CommandOutcome, Int, List<OutgoingEvent>>>(
            Triple(CommandOutcome.Duplicate, 0, listOf(canceled)),
            Triple(outcome, handlerCalls, outbox.events),
        )
    }

    @Test
    fun `given a processed command without response when running again then nothing is published`() {
        inbox.entries["tx:RELEASE_ORDER"] = null

        val outcome = runner.run("tx:RELEASE_ORDER") { CommandEffect() }

        assertEquals<Pair<CommandOutcome, List<OutgoingEvent>>>(CommandOutcome.Duplicate to emptyList(), outcome to outbox.events)
    }

    @Test
    fun `given any command when running then the lock timeout is applied first`() {
        runner.run("tx:MARK_STOCK_PENDING") { CommandEffect() }

        assertEquals(true, lockTimeoutApplied)
    }
}
