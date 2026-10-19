package com.fiap.mechanicalhub.orders.application.usecases.order

import com.fiap.mechanicalhub.orders.application.ports.RepairRepository
import com.fiap.mechanicalhub.orders.application.ports.ServiceOrderFilter
import com.fiap.mechanicalhub.orders.application.ports.ServiceOrderRepository
import com.fiap.mechanicalhub.orders.domain.model.ServiceOrder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Ordem acompanhada do nome de cada reparo do catalogo. */
data class ServiceOrderDetails(
    val order: ServiceOrder,
    val repairNames: Map<UUID, String>,
)

/** GET /orders/{orderId} e GET /orders. */
@Service
class QueryServiceOrdersUseCase(
    private val serviceOrderRepository: ServiceOrderRepository,
    private val repairRepository: RepairRepository,
) {
    @Transactional(readOnly = true)
    fun findById(orderId: UUID): ServiceOrderDetails = withRepairNames(listOf(serviceOrderRepository.requireById(orderId))).single()

    @Transactional(readOnly = true)
    fun search(filter: ServiceOrderFilter): List<ServiceOrderDetails> = withRepairNames(serviceOrderRepository.search(filter))

    private fun withRepairNames(orders: List<ServiceOrder>): List<ServiceOrderDetails> {
        val names = repairRepository.findAllByIds(orders.flatMap { it.repairIds }.toSet()).associate { it.id to it.name }
        return orders.map { ServiceOrderDetails(it, names) }
    }
}
