package com.fiap.mechanicalhub.orders.application.usecases.order

import com.fiap.mechanicalhub.orders.application.ports.RepairRepository
import com.fiap.mechanicalhub.orders.application.ports.ServiceOrderRepository
import com.fiap.mechanicalhub.orders.domain.exceptions.OrderRuleViolationException
import com.fiap.mechanicalhub.orders.domain.exceptions.RepairNotFoundException
import com.fiap.mechanicalhub.orders.domain.exceptions.ServiceOrderNotFoundException
import com.fiap.mechanicalhub.orders.domain.model.ServiceOrder
import java.util.UUID

internal fun ServiceOrderRepository.requireById(orderId: UUID): ServiceOrder =
    findById(orderId) ?: throw ServiceOrderNotFoundException(orderId.toString())

/** Todo reparo informado precisa existir no catalogo e estar ativo. */
internal fun RepairRepository.requireActive(repairIds: Collection<UUID>) {
    val active =
        findAllByIds(repairIds)
            .filter { it.active }
            .map { it.id }
            .toSet()
    val invalid = repairIds.filterNot { it in active }
    if (invalid.isNotEmpty()) throw RepairNotFoundException(invalid)
}

internal fun ServiceOrder.requireSaga(): UUID =
    sagaTransactionId ?: throw OrderRuleViolationException("Ordem $id nao possui transacao de saga")
