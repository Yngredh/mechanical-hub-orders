package com.fiap.mechanicalhub.orders.infrastructure.notification

import com.fiap.mechanicalhub.orders.application.notifications.Notification
import com.fiap.mechanicalhub.orders.infrastructure.notification.channels.NotificationMessage
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

/** Textos das notificacoes (pt-BR). */
class NotificationMessages(
    private val frontendBaseUrl: String,
) {
    private val currency = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"))

    /** Link de decisao do cliente: `https://<frontend>/orders/{orderId}/approval?token={approvalToken}`. */
    fun approvalLink(
        orderId: UUID,
        approvalToken: String,
    ): String = "${frontendBaseUrl.trimEnd('/')}/orders/$orderId/approval?token=$approvalToken"

    fun budgetReady(
        notification: Notification.BudgetReady,
        customerName: String,
    ) = NotificationMessage(
        subject = "Orçamento da ordem ${notification.orderNumber} disponível",
        body =
            "Olá, $customerName! O orçamento da ordem de serviço ${notification.orderNumber} ficou em " +
                "${notification.budget.asCurrency()}. Aprove ou recuse em: " +
                approvalLink(notification.orderId, notification.approvalToken.value),
    )

    fun paymentLinkReady(
        notification: Notification.PaymentLinkReady,
        customerName: String,
    ) = NotificationMessage(
        subject = "Pagamento da ordem ${notification.orderNumber}",
        body =
            "Olá, $customerName! Seu orçamento da ordem ${notification.orderNumber} foi aprovado. " +
                "Pague pelo link: ${notification.paymentLink}",
    )

    fun orderReleased(
        notification: Notification.OrderReleased,
        mechanicName: String,
    ) = NotificationMessage(
        subject = "Ordem ${notification.orderNumber} liberada para execução",
        body = "Olá, $mechanicName! O pagamento da ordem ${notification.orderNumber} foi confirmado; a execução já pode começar.",
    )

    private fun BigDecimal.asCurrency(): String = currency.format(this)
}
