package com.fiap.mechanicalhub.orders.infrastructure.messaging.outbox

import com.fiap.mechanicalhub.orders.IntegrationTest
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.patch
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import kotlin.test.assertEquals

class OutboxRelayIntegrationTest : IntegrationTest() {
    @Autowired
    private lateinit var sqsAsyncClient: SqsAsyncClient

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    @Test
    fun `given diagnosis finished when the relay runs then the envelope reaches the orchestrator queue grouped by order`() {
        val order = givenOrder()

        mockMvc
            .patch("/orders/${order.id}/status") {
                asMechanic()
                contentType = MediaType.APPLICATION_JSON
                content = """{"status":"PREPARING"}"""
            }.andExpect { status { isOk() } }

        val message =
            await atMost Duration.ofSeconds(TIMEOUT_SECONDS) untilNotNull {
                receiveFromOrchestratorQueue().firstOrNull { it.body().contains(order.id.toString()) }
            }
        val envelope = jsonMapper.readTree(message.body())
        assertEquals("diagnosisCompleted", envelope["type"].asString())
        assertEquals(order.id.toString(), message.attributes()[MessageSystemAttributeName.MESSAGE_GROUP_ID])
        assertEquals(envelope["eventId"].asString(), message.attributes()[MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID])
        await atMost Duration.ofSeconds(TIMEOUT_SECONDS) untilNotNull {
            jdbcTemplate
                .queryForObject("SELECT status FROM outbox_events WHERE aggregate_id = ?", String::class.java, order.id)
                ?.takeIf { it == "SENT" }
        }
    }

    private fun receiveFromOrchestratorQueue() =
        sqsAsyncClient
            .receiveMessage(
                ReceiveMessageRequest
                    .builder()
                    .queueUrl(queueUrl())
                    .maxNumberOfMessages(MAX_MESSAGES)
                    .messageSystemAttributeNames(MessageSystemAttributeName.ALL)
                    .waitTimeSeconds(1)
                    .build(),
            ).join()
            .messages()

    private fun queueUrl() = sqsAsyncClient.getQueueUrl { it.queueName("mechanical-hub-orchestrator-events.fifo") }.join().queueUrl()

    private companion object {
        const val TIMEOUT_SECONDS = 15L
        const val MAX_MESSAGES = 10
    }
}
