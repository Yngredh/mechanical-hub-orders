package com.fiap.mechanicalhub.orders.application.usecases.commands

import com.fiap.mechanicalhub.orders.application.commands.OrderCommand
import com.fiap.mechanicalhub.orders.application.events.OutgoingEvent
import com.fiap.mechanicalhub.orders.application.notifications.Notification
import com.fiap.mechanicalhub.orders.application.ports.ServiceOrderRepository
import com.fiap.mechanicalhub.orders.application.usecases.order.requireById
import com.fiap.mechanicalhub.orders.domain.approval.ApprovalToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom

/** `sendApproval` → `WAITING_APPROVAL`; token novo so no primeiro processamento (TD_finishedOS.md 11). */
@Service
class RequestApprovalUseCase(
    private val serviceOrderRepository: ServiceOrderRepository,
    private val runner: IdempotentCommandRunner,
    private val secureRandom: SecureRandom,
) {
    @Transactional
    fun execute(command: OrderCommand.SendApproval): CommandOutcome =
        runner.run(command.idempotencyKey) {
            val token = ApprovalToken.generate(secureRandom)
            val order =
                serviceOrderRepository.save(
                    serviceOrderRepository
                        .requireById(command.orderId)
                        .requestApproval(command.transactionId, command.orderCost, token.hash()),
                )
            CommandEffect(
                notification = Notification.BudgetReady(order.id, order.orderNumber, order.customerId, command.orderCost, token),
            )
        }
}

/** `markStockPending` → `has_stock_pending = true` (TD_PendenciaEstoque.md P3). */
@Service
class MarkStockPendingUseCase(
    private val serviceOrderRepository: ServiceOrderRepository,
    private val runner: IdempotentCommandRunner,
) {
    @Transactional
    fun execute(command: OrderCommand.MarkStockPending): CommandOutcome =
        runner.run(command.idempotencyKey) {
            serviceOrderRepository.save(serviceOrderRepository.requireById(command.orderId).markStockPending(command.transactionId))
            CommandEffect()
        }
}

/** `clearStockPending` → `has_stock_pending = false` (TD_PendenciaEstoque.md P6). */
@Service
class ClearStockPendingUseCase(
    private val serviceOrderRepository: ServiceOrderRepository,
    private val runner: IdempotentCommandRunner,
) {
    @Transactional
    fun execute(command: OrderCommand.ClearStockPending): CommandOutcome =
        runner.run(command.idempotencyKey) {
            serviceOrderRepository.save(serviceOrderRepository.requireById(command.orderId).clearStockPending())
            CommandEffect()
        }
}

/** `sendPaymentLink` → `AWAITING_PAYMENT` e link ao cliente (TD_finishedOS.md 15). */
@Service
class AwaitPaymentUseCase(
    private val serviceOrderRepository: ServiceOrderRepository,
    private val runner: IdempotentCommandRunner,
) {
    @Transactional
    fun execute(command: OrderCommand.SendPaymentLink): CommandOutcome =
        runner.run(command.idempotencyKey) {
            val order = serviceOrderRepository.save(serviceOrderRepository.requireById(command.orderId).awaitPayment())
            CommandEffect(
                notification = Notification.PaymentLinkReady(order.id, order.orderNumber, order.customerId, command.paymentLink),
            )
        }
}

/** `releaseOrder` → `READY_FOR_EXECUTION` e aviso ao mecanico (TD_finishedOS.md 18). */
@Service
class ReleaseOrderUseCase(
    private val serviceOrderRepository: ServiceOrderRepository,
    private val runner: IdempotentCommandRunner,
) {
    @Transactional
    fun execute(command: OrderCommand.ReleaseOrder): CommandOutcome =
        runner.run(command.idempotencyKey) {
            val order = serviceOrderRepository.save(serviceOrderRepository.requireById(command.orderId).releaseForExecution())
            CommandEffect(notification = Notification.OrderReleased(order.id, order.orderNumber, order.responsibleUserId))
        }
}

/** `cancelOrder` → `CANCELED` + `orderCanceled` (TD_paymentFailed.md F5). Sem notificacao ao cliente. */
@Service
class CancelOrderUseCase(
    private val serviceOrderRepository: ServiceOrderRepository,
    private val runner: IdempotentCommandRunner,
) {
    @Transactional
    fun execute(command: OrderCommand.CancelOrder): CommandOutcome =
        runner.run(command.idempotencyKey) {
            val order = serviceOrderRepository.save(serviceOrderRepository.requireById(command.orderId).cancel())
            CommandEffect(response = OutgoingEvent.OrderCanceled(command.transactionId, order.id))
        }
}
