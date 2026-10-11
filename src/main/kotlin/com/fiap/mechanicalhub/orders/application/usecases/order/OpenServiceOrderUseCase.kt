package com.fiap.mechanicalhub.orders.application.usecases.order

import com.fiap.mechanicalhub.orders.application.ports.CustomerRepository
import com.fiap.mechanicalhub.orders.application.ports.RepairRepository
import com.fiap.mechanicalhub.orders.application.ports.ServiceOrderRepository
import com.fiap.mechanicalhub.orders.application.ports.VehicleRepository
import com.fiap.mechanicalhub.orders.domain.exceptions.CustomerNotFoundException
import com.fiap.mechanicalhub.orders.domain.exceptions.InvalidOrderRequestException
import com.fiap.mechanicalhub.orders.domain.exceptions.VehicleNotFoundException
import com.fiap.mechanicalhub.orders.domain.model.NewServiceOrder
import com.fiap.mechanicalhub.orders.domain.model.ServiceOrder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

data class OpenServiceOrderCommand(
    val customerId: UUID,
    val vehicleId: UUID,
    val repairIds: List<UUID>,
    val requestDescription: String,
    val userId: UUID,
)

/** POST /orders: abre a ordem ja em diagnostico para cliente e veiculo existentes. */
@Service
class OpenServiceOrderUseCase(
    private val serviceOrderRepository: ServiceOrderRepository,
    private val customerRepository: CustomerRepository,
    private val vehicleRepository: VehicleRepository,
    private val repairRepository: RepairRepository,
    private val clock: Clock,
) {
    @Transactional
    fun execute(command: OpenServiceOrderCommand): ServiceOrder {
        requireCustomerVehicle(command.customerId, command.vehicleId)
        repairRepository.requireActive(command.repairIds)

        val now = clock.instant()
        val yearMonth = now.atZone(ZoneOffset.UTC).format(YEAR_MONTH)
        val sequence = serviceOrderRepository.nextOrderSequence().toString().padStart(ORDER_SEQUENCE_DIGITS, PAD)
        val order =
            ServiceOrder.open(
                NewServiceOrder(
                    id = UUID.randomUUID(),
                    orderNumber = "OS-$yearMonth-$sequence",
                    customerId = command.customerId,
                    vehicleId = command.vehicleId,
                    createdByUserId = command.userId,
                    requestDescription = command.requestDescription,
                    repairIds = command.repairIds,
                    openedAt = now,
                ),
                UUID::randomUUID,
            )
        return serviceOrderRepository.save(order)
    }

    /** Cliente e veiculo precisam existir, e o veiculo precisa ser do cliente. */
    private fun requireCustomerVehicle(
        customerId: UUID,
        vehicleId: UUID,
    ) {
        customerRepository.findById(customerId) ?: throw CustomerNotFoundException(customerId)
        val vehicle = vehicleRepository.findById(vehicleId) ?: throw VehicleNotFoundException(vehicleId)
        if (vehicle.customerId != customerId) {
            throw InvalidOrderRequestException("Veiculo $vehicleId nao pertence ao cliente $customerId")
        }
    }

    private companion object {
        val YEAR_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMM")
        const val ORDER_SEQUENCE_DIGITS = 4
        const val PAD = '0'
    }
}
