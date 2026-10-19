package com.fiap.mechanicalhub.orders.application.usecases.order

import com.fiap.mechanicalhub.orders.application.ports.ServiceOrderRepository
import com.fiap.mechanicalhub.orders.domain.enums.TaskStatusEnum
import com.fiap.mechanicalhub.orders.domain.model.ServiceOrder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** POST /orders/status: status do reparo durante a execucao; nao movimenta estoque. */
@Service
class UpdateTaskStatusUseCase(
    private val serviceOrderRepository: ServiceOrderRepository,
    private val clock: Clock,
) {
    @Transactional
    fun execute(
        orderId: UUID,
        repairId: UUID,
        status: TaskStatusEnum,
    ): ServiceOrder {
        val order = serviceOrderRepository.requireById(orderId)
        return serviceOrderRepository.save(order.updateTaskStatus(repairId, status, clock.instant()))
    }
}
