package com.fiap.mechanicalhub.orders.application.notifications

import com.fiap.mechanicalhub.orders.domain.approval.ApprovalToken
import java.math.BigDecimal
import java.util.UUID

/** Notificacoes enviadas apos o COMMIT do comando que as originou. */
sealed interface Notification {
    val orderId: UUID

    /** Orcamento pronto: resumo, valor e link de decisao com o token. */
    data class BudgetReady(
        override val orderId: UUID,
        val orderNumber: String,
        val customerId: UUID,
        val budget: BigDecimal,
        val approvalToken: ApprovalToken,
    ) : Notification

    data class PaymentLinkReady(
        override val orderId: UUID,
        val orderNumber: String,
        val customerId: UUID,
        val paymentLink: String,
    ) : Notification

    /** Pagamento confirmado: o mecanico responsavel pode iniciar a execucao. */
    data class OrderReleased(
        override val orderId: UUID,
        val orderNumber: String,
        val responsibleUserId: UUID?,
    ) : Notification
}
