package com.fiap.mechanicalhub.orders.fixtures

import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum
import com.fiap.mechanicalhub.orders.domain.enums.TaskStatusEnum
import com.fiap.mechanicalhub.orders.domain.model.OrderTask
import com.fiap.mechanicalhub.orders.domain.model.ServiceOrder
import java.time.Instant
import java.util.UUID

object Seeds {
    val OIL_CHANGE: UUID = UUID.fromString("03d94934-6201-449b-b8db-490546c35d34")
    val BRAKES: UUID = UUID.fromString("b9090e29-c8f8-498f-b536-bb70cb69113e")
    val CUSTOMER_CARLOS: UUID = UUID.fromString("96052f8d-0817-4124-84ad-918b7c9ac73d")
    val VEHICLE_GOL: UUID = UUID.fromString("d0803851-6a5f-4fea-8463-a723be0e390c")
    val VEHICLE_KWID: UUID = UUID.fromString("241ad3cc-f878-49d3-9292-94ca1d4912d9")
    val MECHANIC: UUID = UUID.fromString("7a2e9b41-3c5d-4f60-8e1a-000000000002")
}

val FIXED_NOW: Instant = Instant.parse("2026-10-11T12:00:00Z")

fun aServiceOrder(
    status: OrderStatusEnum = OrderStatusEnum.IN_DIAGNOSIS,
    taskStatuses: List<TaskStatusEnum> = listOf(TaskStatusEnum.PENDING),
    hasStockPending: Boolean = false,
    sagaTransactionId: UUID? = null,
    approvalTokenHash: String? = null,
): ServiceOrder =
    ServiceOrder(
        id = UUID.randomUUID(),
        orderNumber = "OS-202610-0001",
        customerId = Seeds.CUSTOMER_CARLOS,
        vehicleId = Seeds.VEHICLE_GOL,
        createdByUserId = Seeds.MECHANIC,
        responsibleUserId = Seeds.MECHANIC,
        status = status,
        requestDescription = "Barulho no motor",
        tasks = taskStatuses.map { OrderTask(id = UUID.randomUUID(), repairId = UUID.randomUUID(), status = it) },
        openedAt = FIXED_NOW,
        hasStockPending = hasStockPending,
        sagaTransactionId = sagaTransactionId,
        approvalTokenHash = approvalTokenHash,
    )
