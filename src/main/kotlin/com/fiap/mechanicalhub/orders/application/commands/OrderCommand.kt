package com.fiap.mechanicalhub.orders.application.commands

import java.math.BigDecimal
import java.util.UUID

/**
 * Comandos do orquestrador consumidos de `mechanical-hub-orders-commands.fifo`
 * (mechanical-hub-orchestrator-spec.md §3). Deduplicados por
 * `idempotencyKey = {transactionId}:{STEP}`.
 */
sealed interface OrderCommand {
    val transactionId: UUID
    val orderId: UUID
    val idempotencyKey: String

    data class SendApproval(
        override val transactionId: UUID,
        override val orderId: UUID,
        override val idempotencyKey: String,
        val budgetId: UUID,
        val orderCost: BigDecimal,
    ) : OrderCommand

    data class MarkStockPending(
        override val transactionId: UUID,
        override val orderId: UUID,
        override val idempotencyKey: String,
    ) : OrderCommand

    data class ClearStockPending(
        override val transactionId: UUID,
        override val orderId: UUID,
        override val idempotencyKey: String,
    ) : OrderCommand

    data class SendPaymentLink(
        override val transactionId: UUID,
        override val orderId: UUID,
        override val idempotencyKey: String,
        val paymentLink: String,
    ) : OrderCommand

    data class ReleaseOrder(
        override val transactionId: UUID,
        override val orderId: UUID,
        override val idempotencyKey: String,
    ) : OrderCommand

    data class CancelOrder(
        override val transactionId: UUID,
        override val orderId: UUID,
        override val idempotencyKey: String,
        val reason: String,
    ) : OrderCommand
}
