package com.fiap.mechanicalhub.orders.infrastructure.messaging.outbox

import com.fiap.mechanicalhub.orders.infrastructure.config.OrdersProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

@Configuration
class OutboxRelayConfig {
    @Bean
    fun outboxRelay(
        store: OutboxStore,
        publisher: OutboxMessagePublisher,
        transactionTemplate: TransactionTemplate,
        clock: Clock,
        properties: OrdersProperties,
    ): OutboxRelay = OutboxRelay(store, publisher, transactionTemplate, clock, properties.outbox.batchSize)
}

/** Dispara o relay a cada `fixed-delay` (1 s); desligavel por propriedade. */
@Component
@ConditionalOnProperty(prefix = "mechanical-hub.orders.outbox", name = ["relay-enabled"], havingValue = "true", matchIfMissing = true)
class OutboxRelayScheduler(
    private val outboxRelay: OutboxRelay,
) {
    @Scheduled(fixedDelayString = "\${mechanical-hub.orders.outbox.fixed-delay:1000}")
    fun relay() {
        outboxRelay.relayBatch()
    }
}
