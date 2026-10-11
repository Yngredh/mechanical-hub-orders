package com.fiap.mechanicalhub.orders.infrastructure.config

import io.awspring.cloud.sqs.operations.SqsTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.services.sqs.SqsAsyncClient

@Configuration
class SqsConfig {
    /**
     * O corpo ja e o envelope JSON. Sem o header de tipo Java: os message
     * attributes (limite de 10) ficam livres para o contexto de trace.
     */
    @Bean
    fun sqsTemplate(sqsAsyncClient: SqsAsyncClient): SqsTemplate =
        SqsTemplate
            .builder()
            .sqsAsyncClient(sqsAsyncClient)
            .configureDefaultConverter { it.doNotSendPayloadTypeHeader() }
            .build()
}
