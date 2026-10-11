package com.fiap.mechanicalhub.orders.application.usecases.order

import com.fiap.mechanicalhub.orders.application.ports.RepairRepository
import com.fiap.mechanicalhub.orders.application.ports.ServiceOrderRepository
import com.fiap.mechanicalhub.orders.domain.model.ServiceOrder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** PATCH /orders/{orderId}/repairs: somente com a ordem em diagnostico. */
@Service
class AddRepairsUseCase(
    private val serviceOrderRepository: ServiceOrderRepository,
    private val repairRepository: RepairRepository,
) {
    @Transactional
    fun execute(
        orderId: UUID,
        repairIds: List<UUID>,
    ): ServiceOrder {
        val order = serviceOrderRepository.requireById(orderId)
        repairRepository.requireActive(repairIds)
        return serviceOrderRepository.save(order.addRepairs(repairIds, UUID::randomUUID))
    }
}
