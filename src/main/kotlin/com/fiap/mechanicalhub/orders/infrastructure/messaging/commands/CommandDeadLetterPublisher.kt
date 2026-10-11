package com.fiap.mechanicalhub.orders.infrastructure.messaging.commands

import com.fiap.mechanicalhub.orders.infrastructure.config.OrdersProperties
import io.awspring.cloud.sqs.listener.SqsHeaders
import io.awspring.cloud.sqs.operations.SqsTemplate
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Erro permanente (failure_handling.md §3): reenvia o mesmo corpo a
 * `mechanical-hub-orders-commands-dlq.fifo`, mantendo o contexto de trace e
 * acrescentando `errorClass` e `errorMessage` (limite de 10 atributos).
 */
@Component
class CommandDeadLetterPublisher(
    private val sqsTemplate: SqsTemplate,
    private val properties: OrdersProperties,
) {
    fun publish(
        message: Message<String>,
        error: Throwable,
    ) {
        val headers = message.headers
        val groupId = headers[SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER]?.toString() ?: DEFAULT_GROUP
        val deduplicationId =
            headers[SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_DEDUPLICATION_ID_HEADER]?.toString()
                ?: UUID.randomUUID().toString()
        val attributes =
            TRACE_HEADERS.mapNotNull { name -> headers[name]?.let { name to it.toString() } }.toMap() +
                mapOf(
                    "errorClass" to error.javaClass.name,
                    "errorMessage" to error.message.orEmpty().take(MAX_ERROR_LENGTH),
                )

        sqsTemplate.send<String> { options ->
            options
                .queue(properties.queues.ordersCommandsDlq)
                .payload(message.payload)
                .headers(attributes)
                .messageGroupId(groupId)
                .messageDeduplicationId(deduplicationId)
        }
    }

    private companion object {
        /** Propagacao W3C usada pelo agente OpenTelemetry nos message attributes. */
        val TRACE_HEADERS = listOf("traceparent", "tracestate", "baggage")
        const val DEFAULT_GROUP = "unknown"
        const val MAX_ERROR_LENGTH = 1_000
    }
}
