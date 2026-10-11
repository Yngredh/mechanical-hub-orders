package com.fiap.mechanicalhub.orders.infrastructure.persistence.mappers

import com.fiap.mechanicalhub.orders.domain.model.Customer
import com.fiap.mechanicalhub.orders.domain.model.OrderTask
import com.fiap.mechanicalhub.orders.domain.model.Repair
import com.fiap.mechanicalhub.orders.domain.model.ServiceOrder
import com.fiap.mechanicalhub.orders.domain.model.Vehicle
import com.fiap.mechanicalhub.orders.infrastructure.persistence.entities.CustomerEntity
import com.fiap.mechanicalhub.orders.infrastructure.persistence.entities.OrderTaskEntity
import com.fiap.mechanicalhub.orders.infrastructure.persistence.entities.RepairEntity
import com.fiap.mechanicalhub.orders.infrastructure.persistence.entities.ServiceOrderEntity
import com.fiap.mechanicalhub.orders.infrastructure.persistence.entities.VehicleEntity
import java.time.Instant

fun ServiceOrderEntity.toDomain(): ServiceOrder =
    ServiceOrder(
        id = id,
        orderNumber = orderNumber,
        customerId = customerId,
        vehicleId = vehicleId,
        createdByUserId = createdByUserId,
        responsibleUserId = responsibleUserId,
        status = status,
        requestDescription = requestDescription,
        tasks = tasks.map { it.toDomain() },
        openedAt = openedAt,
        budget = budget,
        hasStockPending = hasStockPending,
        sagaTransactionId = sagaTransactionId,
        approvalTokenHash = approvalTokenHash,
        completedAt = completedAt,
        deliveredAt = deliveredAt,
        version = version,
    )

fun ServiceOrder.toNewEntity(now: Instant): ServiceOrderEntity =
    ServiceOrderEntity(
        id = id,
        orderNumber = orderNumber,
        customerId = customerId,
        vehicleId = vehicleId,
        createdByUserId = createdByUserId,
        responsibleUserId = responsibleUserId,
        status = status,
        requestDescription = requestDescription,
        budget = budget,
        hasStockPending = hasStockPending,
        sagaTransactionId = sagaTransactionId,
        approvalTokenHash = approvalTokenHash,
        openedAt = openedAt,
        completedAt = completedAt,
        deliveredAt = deliveredAt,
        updatedAt = now,
        version = version,
    ).apply { tasks.addAll(this@toNewEntity.tasks.map { it.toEntity() }) }

/** Copia o estado mutavel do agregado para a entidade ja gerenciada pelo JPA. */
fun ServiceOrderEntity.updateFrom(
    order: ServiceOrder,
    now: Instant,
) {
    responsibleUserId = order.responsibleUserId
    status = order.status
    budget = order.budget
    hasStockPending = order.hasStockPending
    sagaTransactionId = order.sagaTransactionId
    approvalTokenHash = order.approvalTokenHash
    completedAt = order.completedAt
    deliveredAt = order.deliveredAt
    updatedAt = now

    val byId = tasks.associateBy { it.id }
    order.tasks.forEach { task ->
        byId[task.id]?.apply {
            status = task.status
            startedAt = task.startedAt
            finishedAt = task.finishedAt
        } ?: tasks.add(task.toEntity())
    }
}

private fun OrderTaskEntity.toDomain() =
    OrderTask(id = id, repairId = repairId, status = status, startedAt = startedAt, finishedAt = finishedAt)

private fun OrderTask.toEntity() =
    OrderTaskEntity(id = id, repairId = repairId, status = status, startedAt = startedAt, finishedAt = finishedAt)

fun CustomerEntity.toDomain() =
    Customer(id = id, name = name, documentNumber = documentNumber, telephone = telephone, email = email)

fun VehicleEntity.toDomain() =
    Vehicle(id = id, customerId = customerId, licensePlate = licensePlate, brand = brand, model = model)

fun RepairEntity.toDomain() = Repair(id = id, name = name, description = description, active = active)
