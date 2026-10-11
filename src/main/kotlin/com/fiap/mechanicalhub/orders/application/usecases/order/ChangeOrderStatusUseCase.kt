package com.fiap.mechanicalhub.orders.application.usecases.order

import com.fiap.mechanicalhub.orders.application.events.OutgoingEvent
import com.fiap.mechanicalhub.orders.application.events.RepairReference
import com.fiap.mechanicalhub.orders.application.ports.OutboxPort
import com.fiap.mechanicalhub.orders.application.ports.ServiceOrderRepository
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum
import com.fiap.mechanicalhub.orders.domain.exceptions.InvalidOrderTransitionException
import com.fiap.mechanicalhub.orders.domain.model.ServiceOrder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * PATCH /orders/{orderId}/status: traduz o status pedido pelo mecanico para o
 * metodo de dominio (TD_finishedOS.md §0.5). Status fora desta lista → 409.
 */
@Service
class ChangeOrderStatusUseCase(
    private val serviceOrderRepository: ServiceOrderRepository,
    private val outbox: OutboxPort,
    private val clock: Clock,
) {
    @Transactional
    fun execute(
        orderId: UUID,
        target: OrderStatusEnum,
    ): ServiceOrder {
        val order = serviceOrderRepository.requireById(orderId)
        val now = clock.instant()
        val (changed, event) =
            when (target) {
                OrderStatusEnum.PREPARING -> {
                    order.finishDiagnosis().let { it to OutgoingEvent.DiagnosisCompleted(it.id, it.repairIds.map(::RepairReference)) }
                }

                OrderStatusEnum.IN_PROGRESS -> {
                    order.startExecution() to null
                }

                OrderStatusEnum.FINISHED -> {
                    order.finish(now).let { it to OutgoingEvent.OrderFinished(it.requireSaga(), it.id) }
                }

                OrderStatusEnum.DELIVERED -> {
                    order.deliver(now) to null
                }

                OrderStatusEnum.RECEIVED,
                OrderStatusEnum.IN_DIAGNOSIS,
                OrderStatusEnum.WAITING_APPROVAL,
                OrderStatusEnum.APPROVED,
                OrderStatusEnum.REJECTED,
                OrderStatusEnum.AWAITING_PAYMENT,
                OrderStatusEnum.READY_FOR_EXECUTION,
                OrderStatusEnum.CANCELED,
                -> {
                    throw InvalidOrderTransitionException(orderId, order.status, "mudar para $target pelo mecanico")
                }
            }
        val saved = serviceOrderRepository.save(changed)
        event?.let(outbox::append)
        return saved
    }
}
