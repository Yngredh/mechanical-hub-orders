package com.fiap.mechanicalhub.orders.infrastructure.notification

import com.fiap.mechanicalhub.orders.application.notifications.Notification
import com.fiap.mechanicalhub.orders.application.ports.CustomerRepository
import com.fiap.mechanicalhub.orders.application.ports.NotificationPort
import com.fiap.mechanicalhub.orders.infrastructure.config.OrdersProperties
import com.fiap.mechanicalhub.orders.infrastructure.notification.channels.NotificationChannel
import com.fiap.mechanicalhub.orders.infrastructure.notification.channels.NotificationMessage
import com.fiap.mechanicalhub.orders.infrastructure.notification.channels.Recipient
import com.fiap.mechanicalhub.orders.infrastructure.persistence.repositories.EmployeeJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Envia a notificacao por todos os canais, depois do COMMIT do comando.
 * Falha em qualquer etapa (destinatario, canal) so gera log: o comando ja foi
 * confirmado e nao e desfeito nem retentado.
 */
@Component
class NotificationAdapter(
    private val customerRepository: CustomerRepository,
    private val employeeRepository: EmployeeJpaRepository,
    private val channels: List<NotificationChannel>,
    properties: OrdersProperties,
) : NotificationPort {
    private val messages = NotificationMessages(properties.frontendBaseUrl)

    override fun send(notification: Notification) {
        runCatching { compose(notification) }
            .onFailure { log.error("Falha ao montar a notificacao da ordem {}: {}", notification.orderId, it.toString()) }
            .getOrNull()
            ?.let { (recipient, message) -> deliver(notification.orderId, recipient, message) }
    }

    private fun compose(notification: Notification): Pair<Recipient, NotificationMessage>? =
        when (notification) {
            is Notification.BudgetReady -> {
                customer(notification.customerId)?.let { it to messages.budgetReady(notification, it.name) }
            }

            is Notification.PaymentLinkReady -> {
                customer(notification.customerId)?.let { it to messages.paymentLinkReady(notification, it.name) }
            }

            is Notification.OrderReleased -> {
                notification.responsibleUserId?.let(::employee)?.let { it to messages.orderReleased(notification, it.name) }
            }
        }

    private fun deliver(
        orderId: UUID,
        recipient: Recipient,
        message: NotificationMessage,
    ) {
        channels.forEach { channel ->
            runCatching { channel.send(recipient, message) }
                .onFailure { log.error("Falha no envio por {} da ordem {}: {}", channel.javaClass.simpleName, orderId, it.toString()) }
        }
    }

    private fun customer(customerId: UUID): Recipient? {
        val customer = customerRepository.findById(customerId)
        if (customer == null) log.warn("Cliente {} nao encontrado para notificacao", customerId)
        return customer?.let { Recipient(it.name, it.email, it.telephone) }
    }

    private fun employee(userId: UUID): Recipient? {
        val employee = employeeRepository.findByIdAndDeletedAtIsNull(userId)
        if (employee == null) log.warn("Funcionario {} nao encontrado para notificacao", userId)
        return employee?.let { Recipient(it.name, it.email, telephone = null) }
    }

    private companion object {
        val log = LoggerFactory.getLogger(NotificationAdapter::class.java)
    }
}
