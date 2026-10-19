package com.fiap.mechanicalhub.orders.application.usecases.customer

import com.fiap.mechanicalhub.orders.application.events.OutgoingEvent
import com.fiap.mechanicalhub.orders.application.ports.CustomerRepository
import com.fiap.mechanicalhub.orders.application.ports.OutboxPort
import com.fiap.mechanicalhub.orders.application.ports.RepairRepository
import com.fiap.mechanicalhub.orders.application.ports.ServiceOrderRepository
import com.fiap.mechanicalhub.orders.application.ports.VehicleRepository
import com.fiap.mechanicalhub.orders.application.usecases.order.requireSaga
import com.fiap.mechanicalhub.orders.domain.approval.matchesApprovalToken
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum
import com.fiap.mechanicalhub.orders.domain.exceptions.ServiceOrderNotFoundException
import com.fiap.mechanicalhub.orders.domain.model.ServiceOrder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Resumo da ordem exibido ao cliente (mesmos campos do monolito). */
data class CustomerOrderView(
    val orderId: UUID,
    val orderNumber: String,
    val customerName: String,
    val vehicleLicensePlate: String,
    val vehicleBrand: String,
    val vehicleModel: String,
    val status: OrderStatusEnum,
    val budget: BigDecimal?,
    val services: List<String>,
    val openedAt: Instant,
    val completedAt: Instant?,
    val deliveredAt: Instant?,
)

/**
 * Token ausente, invalido ou ordem sem token (ainda sem orcamento) → 404, sem
 * revelar se a ordem existe (TD_finishedOS.md passo 12).
 */
private fun ServiceOrder?.authorizedBy(
    approvalToken: String?,
    reference: String,
): ServiceOrder {
    val tokenHash = this?.approvalTokenHash
    if (this == null || tokenHash == null || !tokenHash.matchesApprovalToken(approvalToken)) {
        throw ServiceOrderNotFoundException(reference)
    }
    return this
}

/** GET /mechanical-hub/service-orders/{orderNumber}. */
@Service
class GetCustomerOrderUseCase(
    private val serviceOrderRepository: ServiceOrderRepository,
    private val customerRepository: CustomerRepository,
    private val vehicleRepository: VehicleRepository,
    private val repairRepository: RepairRepository,
) {
    @Transactional(readOnly = true)
    fun execute(
        orderNumber: String,
        approvalToken: String?,
    ): CustomerOrderView {
        val order = serviceOrderRepository.findByOrderNumber(orderNumber).authorizedBy(approvalToken, orderNumber)
        val customer = customerRepository.findById(order.customerId)
        val vehicle = vehicleRepository.findById(order.vehicleId)
        return CustomerOrderView(
            orderId = order.id,
            orderNumber = order.orderNumber,
            customerName = customer?.name.orEmpty(),
            vehicleLicensePlate = vehicle?.licensePlate.orEmpty(),
            vehicleBrand = vehicle?.brand.orEmpty(),
            vehicleModel = vehicle?.model.orEmpty(),
            status = order.status,
            budget = order.budget,
            services = repairRepository.findAllByIds(order.repairIds).map { it.name },
            openedAt = order.openedAt,
            completedAt = order.completedAt,
            deliveredAt = order.deliveredAt,
        )
    }
}

/** POST /mechanical-hub/service-orders/{orderId}/approve: `approve()` + `orderApproved`. */
@Service
class ApproveOrderUseCase(
    private val serviceOrderRepository: ServiceOrderRepository,
    private val outbox: OutboxPort,
) {
    @Transactional
    fun execute(
        orderId: UUID,
        approvalToken: String?,
    ) {
        val order = serviceOrderRepository.findById(orderId).authorizedBy(approvalToken, orderId.toString())
        val approved = serviceOrderRepository.save(order.approve())
        outbox.append(OutgoingEvent.OrderApproved(approved.requireSaga(), approved.id))
    }
}

/** POST /mechanical-hub/service-orders/{orderId}/reject: `reject()` + `orderRejected`. */
@Service
class RejectOrderUseCase(
    private val serviceOrderRepository: ServiceOrderRepository,
    private val outbox: OutboxPort,
) {
    @Transactional
    fun execute(
        orderId: UUID,
        approvalToken: String?,
    ) {
        val order = serviceOrderRepository.findById(orderId).authorizedBy(approvalToken, orderId.toString())
        val rejected = serviceOrderRepository.save(order.reject())
        outbox.append(OutgoingEvent.OrderRejected(rejected.requireSaga(), rejected.id))
    }
}
