package com.fiap.mechanicalhub.orders.infrastructure.messaging.commands

import com.fiap.mechanicalhub.orders.IntegrationTest
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum
import io.awspring.cloud.sqs.operations.SqsTemplate
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OrderCommandListenerIntegrationTest : IntegrationTest() {
    @Autowired
    private lateinit var sqsTemplate: SqsTemplate

    @Autowired
    private lateinit var sqsAsyncClient: SqsAsyncClient

    @Test
    fun `given sendApproval when consumed then the order waits for approval with budget, saga and token hash`() {
        val order = givenOrder(status = OrderStatusEnum.PREPARING)
        val transactionId = UUID.randomUUID()

        sendCommand(order.id, transactionId, "sendApproval", "SEND_APPROVAL", ""","budgetId":"${UUID.randomUUID()}","orderCost":308.10""")

        await atMost TIMEOUT untilAsserted {
            val saved = assertNotNull(serviceOrderRepository.findById(order.id))
            assertEquals(OrderStatusEnum.WAITING_APPROVAL, saved.status)
            assertEquals(transactionId, saved.sagaTransactionId)
            assertEquals("308.10", saved.budget?.toPlainString())
            assertNotNull(saved.approvalTokenHash)
        }
    }

    @Test
    fun `given sendApproval delivered twice when consumed then the token is generated only once`() {
        val order = givenOrder(status = OrderStatusEnum.PREPARING)
        val transactionId = UUID.randomUUID()
        val payload = ""","budgetId":"${UUID.randomUUID()}","orderCost":100.00"""
        sendCommand(order.id, transactionId, "sendApproval", "SEND_APPROVAL", payload)
        val firstHash = await atMost TIMEOUT untilNotNull { serviceOrderRepository.findById(order.id)?.approvalTokenHash }

        sendCommand(order.id, transactionId, "sendApproval", "SEND_APPROVAL", payload)
        sendCommand(order.id, transactionId, "clearStockPending", "CLEAR_STOCK_PENDING_PROBE")

        await atMost TIMEOUT untilAsserted { assertEquals(1, inboxCount("$transactionId:CLEAR_STOCK_PENDING_PROBE")) }
        assertEquals(firstHash, serviceOrderRepository.findById(order.id)?.approvalTokenHash)
        assertEquals(1, inboxCount("$transactionId:SEND_APPROVAL"))
    }

    @Test
    fun `given markStockPending then clearStockPending when consumed then the flag is set and removed in order`() {
        val order = givenOrder(status = OrderStatusEnum.PREPARING)
        val transactionId = UUID.randomUUID()

        sendCommand(order.id, transactionId, "markStockPending", "MARK_STOCK_PENDING")
        sendCommand(order.id, transactionId, "clearStockPending", "CLEAR_STOCK_PENDING")

        await atMost TIMEOUT untilAsserted { assertEquals(1, inboxCount("$transactionId:CLEAR_STOCK_PENDING")) }
        val saved = assertNotNull(serviceOrderRepository.findById(order.id))
        assertEquals(false to transactionId, saved.hasStockPending to saved.sagaTransactionId)
    }

    @Test
    fun `given cancelOrder delivered twice when consumed then the order is canceled once and the response is republished`() {
        val transactionId = UUID.randomUUID()
        val order = givenOrder(status = OrderStatusEnum.AWAITING_PAYMENT, sagaTransactionId = transactionId)

        sendCommand(order.id, transactionId, "cancelOrder", "CANCEL_ORDER", ""","reason":"PAYMENT_NOT_CONFIRMED"""")
        await atMost TIMEOUT untilAsserted { assertEquals(listOf("orderCanceled"), outboxTypes(order.id)) }
        sendCommand(order.id, transactionId, "cancelOrder", "CANCEL_ORDER", ""","reason":"PAYMENT_NOT_CONFIRMED"""")

        await atMost TIMEOUT untilAsserted { assertEquals(listOf("orderCanceled", "orderCanceled"), outboxTypes(order.id)) }
        assertEquals(OrderStatusEnum.CANCELED, serviceOrderRepository.findById(order.id)?.status)
    }

    @Test
    fun `given a command invalid for the order status when consumed then it goes to the dead letter queue with the error`() {
        val order = givenOrder(status = OrderStatusEnum.IN_DIAGNOSIS)
        val transactionId = UUID.randomUUID()

        sendCommand(order.id, transactionId, "releaseOrder", "RELEASE_ORDER")

        val deadLetter =
            await atMost TIMEOUT untilNotNull {
                receiveDeadLetters().firstOrNull { it.body().contains(order.id.toString()) }
            }
        assertEquals(
            "com.fiap.mechanicalhub.orders.domain.exceptions.InvalidOrderTransitionException",
            deadLetter.messageAttributes()["errorClass"]?.stringValue(),
        )
        assertEquals(order.id.toString(), deadLetter.attributes()[MessageSystemAttributeName.MESSAGE_GROUP_ID])
        assertEquals(OrderStatusEnum.IN_DIAGNOSIS, serviceOrderRepository.findById(order.id)?.status)
    }

    private fun sendCommand(
        orderId: UUID,
        transactionId: UUID,
        type: String,
        step: String,
        extra: String = "",
    ) {
        val eventId = UUID.randomUUID()
        val body = """
            {"eventId":"$eventId","transactionId":"$transactionId","orderId":"$orderId","type":"$type","version":1,
             "timestamp":"2026-10-11T12:00:00Z","idempotencyKey":"$transactionId:$step"$extra}
        """
        sqsTemplate.send<String> {
            it
                .queue("mechanical-hub-orders-commands.fifo")
                .payload(body)
                .messageGroupId(orderId.toString())
                .messageDeduplicationId(eventId.toString())
        }
    }

    private fun inboxCount(key: String) =
        jdbcTemplate.queryForObject("SELECT count(*) FROM inbox_events WHERE message_key = ?", Int::class.java, key)

    private fun receiveDeadLetters() =
        sqsAsyncClient
            .receiveMessage {
                it
                    .queueUrl(dlqUrl())
                    .maxNumberOfMessages(MAX_MESSAGES)
                    .messageSystemAttributeNames(MessageSystemAttributeName.ALL)
                    .messageAttributeNames("All")
                    .waitTimeSeconds(1)
            }.join()
            .messages()

    private fun dlqUrl() = sqsAsyncClient.getQueueUrl { it.queueName("mechanical-hub-orders-commands-dlq.fifo") }.join().queueUrl()

    private companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(20)
        const val MAX_MESSAGES = 10
    }
}
