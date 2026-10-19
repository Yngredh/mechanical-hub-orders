package com.fiap.mechanicalhub.orders.infrastructure.messaging.commands

import com.fiap.mechanicalhub.orders.application.commands.OrderCommand
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class OrderCommandParserTest {
    private val parser = OrderCommandParser(JsonMapper.builder().build())
    private val transactionId = UUID.randomUUID()
    private val orderId = UUID.randomUUID()

    @Test
    fun `given a sendApproval envelope when parsing then budget data is read`() {
        val budgetId = UUID.randomUUID()

        val command = parser.parse(envelope("sendApproval", "SEND_APPROVAL", ""","budgetId":"$budgetId","orderCost":308.10"""))

        assertEquals(
            OrderCommand.SendApproval(transactionId, orderId, "$transactionId:SEND_APPROVAL", budgetId, BigDecimal("308.10")),
            command,
        )
    }

    @Test
    fun `given a sendPaymentLink envelope when parsing then the link is read`() {
        val command = parser.parse(envelope("sendPaymentLink", "SEND_PAYMENT_LINK", ""","paymentLink":"https://mp/checkout""""))

        assertEquals("https://mp/checkout", assertIs<OrderCommand.SendPaymentLink>(command).paymentLink)
    }

    @Test
    fun `given a cancelOrder envelope when parsing then the reason is read`() {
        val command = parser.parse(envelope("cancelOrder", "CANCEL_ORDER", ""","reason":"PAYMENT_NOT_CONFIRMED""""))

        assertEquals("PAYMENT_NOT_CONFIRMED", assertIs<OrderCommand.CancelOrder>(command).reason)
    }

    @Test
    fun `given stock pending commands when parsing then both types are recognized`() {
        val mark = parser.parse(envelope("markStockPending", "MARK_STOCK_PENDING"))

        val clear = parser.parse(envelope("clearStockPending", "CLEAR_STOCK_PENDING"))

        assertEquals(listOf(OrderCommand.MarkStockPending::class, OrderCommand.ClearStockPending::class), listOf(mark::class, clear::class))
    }

    @Test
    fun `given a sendApproval without orderCost when parsing then it is outside the contract`() {
        val body = envelope("sendApproval", "SEND_APPROVAL", ""","budgetId":"${UUID.randomUUID()}"""")

        assertFailsWith<InvalidMessageException> { parser.parse(body) }
    }

    @Test
    fun `given an envelope without idempotencyKey when parsing then it is outside the contract`() {
        val body = """{"transactionId":"$transactionId","orderId":"$orderId","type":"releaseOrder"}"""

        assertFailsWith<InvalidMessageException> { parser.parse(body) }
    }

    @Test
    fun `given an invalid uuid when parsing then it is outside the contract`() {
        val body = envelope("releaseOrder", "RELEASE_ORDER").replace(orderId.toString(), "nao-e-uuid")

        assertFailsWith<InvalidMessageException> { parser.parse(body) }
    }

    private fun envelope(
        type: String,
        step: String,
        extra: String = "",
    ) = """
        {"eventId":"${UUID.randomUUID()}","transactionId":"$transactionId","orderId":"$orderId","type":"$type",
         "version":1,"timestamp":"2026-10-11T12:00:00Z","idempotencyKey":"$transactionId:$step"$extra}
    """
}
