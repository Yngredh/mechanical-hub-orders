package com.fiap.mechanicalhub.orders.infrastructure.messaging.outbox

import com.fiap.mechanicalhub.orders.infrastructure.config.OrdersProperties
import io.awspring.cloud.sqs.operations.SqsTemplate
import org.springframework.stereotype.Component

/**
 * Publica no `mechanical-hub-orchestrator-events.fifo` com
 * `MessageGroupId = orderId` e `MessageDeduplicationId = eventId`. O corpo e o
 * envelope JSON gravado no outbox, sem conversao.
 */
@Component
class SqsOutboxMessagePublisher(
    private val sqsTemplate: SqsTemplate,
    private val properties: OrdersProperties,
) : OutboxMessagePublisher {
    override fun publish(message: PendingOutboxMessage) {
        sqsTemplate.send<String> { options ->
            options
                .queue(properties.queues.orchestratorEvents)
                .payload(message.payload)
                .messageGroupId(message.aggregateId.toString())
                .messageDeduplicationId(message.eventId.toString())
        }
    }
}
