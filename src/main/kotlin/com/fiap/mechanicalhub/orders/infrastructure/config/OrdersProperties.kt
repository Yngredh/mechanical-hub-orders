package com.fiap.mechanicalhub.orders.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuracao propria do servico, preenchida por variaveis de ambiente
 * (application.yml). Nenhum valor sensivel: as credenciais AWS vem da LabRole.
 */
@ConfigurationProperties(prefix = "mechanical-hub.orders")
data class OrdersProperties(
    val queues: Queues,
    val frontendBaseUrl: String,
) {
    data class Queues(
        val ordersCommands: String,
        val ordersCommandsDlq: String,
        val orchestratorEvents: String,
    )
}
