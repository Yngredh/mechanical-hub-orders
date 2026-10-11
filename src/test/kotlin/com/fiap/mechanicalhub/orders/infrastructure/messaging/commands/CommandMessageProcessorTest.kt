package com.fiap.mechanicalhub.orders.infrastructure.messaging.commands

import com.fiap.mechanicalhub.orders.application.commands.OrderCommand
import com.fiap.mechanicalhub.orders.application.notifications.Notification
import com.fiap.mechanicalhub.orders.application.usecases.commands.CommandOutcome
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum
import com.fiap.mechanicalhub.orders.domain.exceptions.InvalidOrderTransitionException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.dao.CannotAcquireLockException
import org.springframework.dao.DuplicateKeyException
import org.springframework.transaction.CannotCreateTransactionException
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CommandMessageProcessorTest {
    private val dispatcher = mockk<OrderCommandDispatcher>()
    private val processor = CommandMessageProcessor(OrderCommandParser(JsonMapper.builder().build()), dispatcher)
    private val orderId = UUID.randomUUID()
    private val transactionId = UUID.randomUUID()
    private val releaseOrder = """
        {"eventId":"${UUID.randomUUID()}","transactionId":"$transactionId","orderId":"$orderId","type":"releaseOrder",
         "version":1,"timestamp":"2026-10-11T12:00:00Z","idempotencyKey":"$transactionId:RELEASE_ORDER"}
    """

    @Test
    fun `given an applied command when processing then the message is acknowledged with its notification`() {
        val notification = Notification.OrderReleased(orderId, "OS-1", null)
        every { dispatcher.dispatch(any()) } returns CommandOutcome.Applied(notification)

        val decision = processor.process(releaseOrder, receiveCount = 1)

        assertEquals(CommandDecision.Acknowledge(notification), decision)
    }

    @Test
    fun `given a duplicate command when processing then it is acknowledged without notification`() {
        every { dispatcher.dispatch(any()) } returns CommandOutcome.Duplicate

        val decision = processor.process(releaseOrder, receiveCount = 2)

        assertEquals(CommandDecision.Acknowledge(null), decision)
    }

    @Test
    fun `given an invalid transition when processing then it goes to the dead letter queue`() {
        every { dispatcher.dispatch(any()) } throws InvalidOrderTransitionException(orderId, OrderStatusEnum.IN_DIAGNOSIS, "liberar")

        val decision = processor.process(releaseOrder, receiveCount = 1)

        assertIs<CommandDecision.DeadLetter>(decision)
    }

    @Test
    fun `given a body that is not json when processing then it goes to the dead letter queue`() {
        val decision = processor.process("not-json", receiveCount = 1)

        assertIs<CommandDecision.DeadLetter>(decision)
    }

    @Test
    fun `given an unknown command type when processing then it goes to the dead letter queue`() {
        val body = releaseOrder.replace("releaseOrder", "launchRocket")

        val decision = processor.process(body, receiveCount = 1)

        assertIs<CommandDecision.DeadLetter>(decision)
    }

    @ParameterizedTest(name = "recebimento {0} → {1} s")
    @CsvSource("1,1", "2,2", "3,4", "4,8", "5,8", "6,8")
    fun `given the database is down when processing then it retries with exponential backoff`(
        receiveCount: Int,
        expectedDelay: Int,
    ) {
        every { dispatcher.dispatch(any()) } throws CannotCreateTransactionException("connection refused")

        val decision = processor.process(releaseOrder, receiveCount)

        assertEquals(expectedDelay, assertIs<CommandDecision.Retry>(decision).delaySeconds)
    }

    @Test
    fun `given a lock timeout when processing then it is retried`() {
        every { dispatcher.dispatch(any()) } throws CannotAcquireLockException("lock_timeout")

        val decision = processor.process(releaseOrder, receiveCount = 1)

        assertIs<CommandDecision.Retry>(decision)
    }

    @Test
    fun `given a concurrent duplicate insert in the inbox when processing then it is retried`() {
        every { dispatcher.dispatch(any()) } throws DuplicateKeyException("inbox_events_pkey")

        val decision = processor.process(releaseOrder, receiveCount = 1)

        assertIs<CommandDecision.Retry>(decision)
    }

    @Test
    fun `given a valid envelope when processing then the parsed command reaches the dispatcher`() {
        var dispatched: OrderCommand? = null
        every { dispatcher.dispatch(any()) } answers {
            dispatched = firstArg()
            CommandOutcome.Applied(null)
        }

        processor.process(releaseOrder, receiveCount = 1)

        assertEquals(OrderCommand.ReleaseOrder(transactionId, orderId, "$transactionId:RELEASE_ORDER"), dispatched)
    }
}
