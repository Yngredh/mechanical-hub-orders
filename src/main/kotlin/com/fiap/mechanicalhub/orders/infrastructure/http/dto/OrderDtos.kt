package com.fiap.mechanicalhub.orders.infrastructure.http.dto

import com.fiap.mechanicalhub.orders.application.usecases.customer.CustomerOrderView
import com.fiap.mechanicalhub.orders.application.usecases.order.ServiceOrderDetails
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum
import com.fiap.mechanicalhub.orders.domain.enums.TaskStatusEnum
import com.fiap.mechanicalhub.orders.domain.model.Repair
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Mesmo formato do `/open` do monolito. */
data class OpenServiceOrderRequest(
    @field:NotNull val customerId: UUID?,
    @field:NotNull val vehicleId: UUID?,
    @field:NotEmpty val serviceIds: List<UUID>?,
    @field:NotBlank @field:Size(max = 255) val requestDescription: String?,
)

data class AddRepairsRequest(
    @field:NotEmpty @field:Valid val repairs: List<RepairIdRequest>?,
)

data class RepairIdRequest(
    @field:NotNull val repairId: UUID?,
)

data class ChangeOrderStatusRequest(
    @field:NotNull val status: OrderStatusEnum?,
)

data class UpdateTaskStatusRequest(
    @field:NotNull val orderId: UUID?,
    @field:NotNull val repairId: UUID?,
    @field:NotNull val status: TaskStatusEnum?,
)

data class StatusResponse(
    val code: String,
    val displayName: String,
)

data class OrderTaskResponse(
    val repairId: UUID,
    val name: String?,
    val status: StatusResponse,
    val startedAt: Instant?,
    val finishedAt: Instant?,
)

data class ServiceOrderResponse(
    val id: UUID,
    val orderNumber: String,
    val status: StatusResponse,
    val customerId: UUID,
    val vehicleId: UUID,
    val createdByUserId: UUID,
    val responsibleUserId: UUID?,
    val requestDescription: String,
    val budget: BigDecimal?,
    val hasStockPending: Boolean,
    val repairs: List<OrderTaskResponse>,
    val openedAt: Instant,
    val completedAt: Instant?,
    val deliveredAt: Instant?,
)

data class RepairResponse(
    val id: UUID,
    val name: String,
    val description: String?,
)

data class CustomerOrderResponse(
    val orderId: UUID,
    val orderNumber: String,
    val customerName: String,
    val vehicleLicensePlate: String,
    val vehicleModel: String,
    val vehicleBrand: String,
    val status: StatusResponse,
    val budget: BigDecimal?,
    val services: List<String>,
    val openedAt: Instant,
    val completedAt: Instant?,
    val deliveredAt: Instant?,
)

fun OrderStatusEnum.toResponse() = StatusResponse(name, displayName)

fun TaskStatusEnum.toResponse() = StatusResponse(name, displayName)

fun ServiceOrderDetails.toResponse(): ServiceOrderResponse =
    ServiceOrderResponse(
        id = order.id,
        orderNumber = order.orderNumber,
        status = order.status.toResponse(),
        customerId = order.customerId,
        vehicleId = order.vehicleId,
        createdByUserId = order.createdByUserId,
        responsibleUserId = order.responsibleUserId,
        requestDescription = order.requestDescription,
        budget = order.budget,
        hasStockPending = order.hasStockPending,
        repairs =
            order.tasks.map {
                OrderTaskResponse(it.repairId, repairNames[it.repairId], it.status.toResponse(), it.startedAt, it.finishedAt)
            },
        openedAt = order.openedAt,
        completedAt = order.completedAt,
        deliveredAt = order.deliveredAt,
    )

fun Repair.toResponse() = RepairResponse(id = id, name = name, description = description)

fun CustomerOrderView.toResponse() =
    CustomerOrderResponse(
        orderId = orderId,
        orderNumber = orderNumber,
        customerName = customerName,
        vehicleLicensePlate = vehicleLicensePlate,
        vehicleModel = vehicleModel,
        vehicleBrand = vehicleBrand,
        status = status.toResponse(),
        budget = budget,
        services = services,
        openedAt = openedAt,
        completedAt = completedAt,
        deliveredAt = deliveredAt,
    )
