package com.fiap.mechanicalhub.orders.infrastructure.messaging.commands

import com.fiap.mechanicalhub.orders.application.commands.OrderCommand
import com.fiap.mechanicalhub.orders.application.usecases.commands.AwaitPaymentUseCase
import com.fiap.mechanicalhub.orders.application.usecases.commands.CancelOrderUseCase
import com.fiap.mechanicalhub.orders.application.usecases.commands.ClearStockPendingUseCase
import com.fiap.mechanicalhub.orders.application.usecases.commands.CommandOutcome
import com.fiap.mechanicalhub.orders.application.usecases.commands.MarkStockPendingUseCase
import com.fiap.mechanicalhub.orders.application.usecases.commands.ReleaseOrderUseCase
import com.fiap.mechanicalhub.orders.application.usecases.commands.RequestApprovalUseCase
import org.springframework.stereotype.Component

/** Comando → caso de uso (cada caso de uso abre e fecha a propria transacao). */
@Component
class OrderCommandDispatcher(
    private val requestApproval: RequestApprovalUseCase,
    private val markStockPending: MarkStockPendingUseCase,
    private val clearStockPending: ClearStockPendingUseCase,
    private val awaitPayment: AwaitPaymentUseCase,
    private val releaseOrder: ReleaseOrderUseCase,
    private val cancelOrder: CancelOrderUseCase,
) {
    fun dispatch(command: OrderCommand): CommandOutcome =
        when (command) {
            is OrderCommand.SendApproval -> requestApproval.execute(command)
            is OrderCommand.MarkStockPending -> markStockPending.execute(command)
            is OrderCommand.ClearStockPending -> clearStockPending.execute(command)
            is OrderCommand.SendPaymentLink -> awaitPayment.execute(command)
            is OrderCommand.ReleaseOrder -> releaseOrder.execute(command)
            is OrderCommand.CancelOrder -> cancelOrder.execute(command)
        }
}
