package com.fiap.mechanicalhub.orders.application.usecases.commands

import com.fiap.mechanicalhub.orders.application.commands.OrderCommand
import com.fiap.mechanicalhub.orders.application.events.OutgoingEvent
import com.fiap.mechanicalhub.orders.application.notifications.Notification
import com.fiap.mechanicalhub.orders.domain.approval.matchesApprovalToken
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum
import com.fiap.mechanicalhub.orders.domain.exceptions.InvalidOrderTransitionException
import com.fiap.mechanicalhub.orders.fixtures.FakeInbox
import com.fiap.mechanicalhub.orders.fixtures.FakeOutbox
import com.fiap.mechanicalhub.orders.fixtures.FakeServiceOrderRepository
import com.fiap.mechanicalhub.orders.fixtures.Seeds
import com.fiap.mechanicalhub.orders.fixtures.aServiceOrder
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.security.SecureRandom
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SagaCommandUseCasesTest {
    private val transactionId = UUID.randomUUID()
    private val inbox = FakeInbox()
    private val outbox = FakeOutbox()
    private val runner = IdempotentCommandRunner(inbox, outbox) {}

    @Test
    fun `given an order in preparation when sendApproval runs then it waits for approval and the token matches the stored hash`() {
        val order = aServiceOrder(status = OrderStatusEnum.PREPARING)
        val orders = FakeServiceOrderRepository(order)
        val command =
            OrderCommand.SendApproval(
                transactionId,
                order.id,
                "$transactionId:SEND_APPROVAL",
                UUID.randomUUID(),
                BigDecimal("308.10"),
            )

        val outcome = RequestApprovalUseCase(orders, runner, SecureRandom()).execute(command)

        val notification = assertIs<Notification.BudgetReady>(assertIs<CommandOutcome.Applied>(outcome).notification)
        val saved = orders.findById(order.id)
        assertEquals(OrderStatusEnum.WAITING_APPROVAL to transactionId, saved?.status to saved?.sagaTransactionId)
        assertTrue(saved?.approvalTokenHash.orEmpty().matchesApprovalToken(notification.approvalToken.value))
    }

    @Test
    fun `given sendApproval already processed when it runs again then the token is kept and nobody is notified`() {
        val order = aServiceOrder(status = OrderStatusEnum.WAITING_APPROVAL, approvalTokenHash = "hash-original")
        val orders = FakeServiceOrderRepository(order)
        val command = OrderCommand.SendApproval(transactionId, order.id, "$transactionId:SEND_APPROVAL", UUID.randomUUID(), BigDecimal.TEN)
        inbox.entries[command.idempotencyKey] = null

        val outcome = RequestApprovalUseCase(orders, runner, SecureRandom()).execute(command)

        assertEquals(CommandOutcome.Duplicate to "hash-original", outcome to orders.findById(order.id)?.approvalTokenHash)
    }

    @Test
    fun `given an order in preparation when markStockPending runs then the flag and the saga are recorded`() {
        val order = aServiceOrder(status = OrderStatusEnum.PREPARING)
        val orders = FakeServiceOrderRepository(order)

        MarkStockPendingUseCase(orders, runner).execute(OrderCommand.MarkStockPending(transactionId, order.id, "$transactionId:MARK"))

        assertEquals(true to transactionId, orders.findById(order.id)?.let { it.hasStockPending to it.sagaTransactionId })
    }

    @Test
    fun `given a pending order when clearStockPending runs then the flag is removed`() {
        val order = aServiceOrder(status = OrderStatusEnum.PREPARING, hasStockPending = true)
        val orders = FakeServiceOrderRepository(order)

        ClearStockPendingUseCase(orders, runner).execute(OrderCommand.ClearStockPending(transactionId, order.id, "$transactionId:CLEAR"))

        assertEquals(false, orders.findById(order.id)?.hasStockPending)
    }

    @Test
    fun `given an approved order when sendPaymentLink runs then it awaits payment and the customer gets the link`() {
        val order = aServiceOrder(status = OrderStatusEnum.APPROVED)
        val orders = FakeServiceOrderRepository(order)
        val command = OrderCommand.SendPaymentLink(transactionId, order.id, "$transactionId:PAYMENT_LINK", "https://mp.example/pay")

        val outcome = AwaitPaymentUseCase(orders, runner).execute(command)

        assertEquals(
            CommandOutcome.Applied(Notification.PaymentLinkReady(order.id, order.orderNumber, order.customerId, "https://mp.example/pay")),
            outcome,
        )
        assertEquals(OrderStatusEnum.AWAITING_PAYMENT, orders.findById(order.id)?.status)
    }

    @Test
    fun `given an order awaiting payment when releaseOrder runs then the responsible mechanic is notified`() {
        val order = aServiceOrder(status = OrderStatusEnum.AWAITING_PAYMENT)
        val orders = FakeServiceOrderRepository(order)

        val outcome =
            ReleaseOrderUseCase(
                orders,
                runner,
            ).execute(OrderCommand.ReleaseOrder(transactionId, order.id, "$transactionId:RELEASE"))

        assertEquals(CommandOutcome.Applied(Notification.OrderReleased(order.id, order.orderNumber, Seeds.MECHANIC)), outcome)
        assertEquals(OrderStatusEnum.READY_FOR_EXECUTION, orders.findById(order.id)?.status)
    }

    @Test
    fun `given an order awaiting payment when cancelOrder runs then it is canceled and orderCanceled is recorded`() {
        val order = aServiceOrder(status = OrderStatusEnum.AWAITING_PAYMENT)
        val orders = FakeServiceOrderRepository(order)
        val command = OrderCommand.CancelOrder(transactionId, order.id, "$transactionId:CANCEL_ORDER", "PAYMENT_EXPIRED")

        CancelOrderUseCase(orders, runner).execute(command)

        assertEquals(OrderStatusEnum.CANCELED, orders.findById(order.id)?.status)
        assertEquals(listOf<OutgoingEvent>(OutgoingEvent.OrderCanceled(transactionId, order.id)), outbox.events)
    }

    @Test
    fun `given an order in diagnosis when cancelOrder runs then it is an invalid transition and nothing is registered`() {
        val order = aServiceOrder(status = OrderStatusEnum.IN_DIAGNOSIS)
        val command = OrderCommand.CancelOrder(transactionId, order.id, "$transactionId:CANCEL_ORDER", "PAYMENT_EXPIRED")

        assertFailsWith<InvalidOrderTransitionException> { CancelOrderUseCase(FakeServiceOrderRepository(order), runner).execute(command) }
        assertEquals(emptyMap(), inbox.entries)
    }
}
