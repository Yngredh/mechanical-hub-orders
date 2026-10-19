package com.fiap.mechanicalhub.orders.fixtures

import com.fiap.mechanicalhub.orders.application.events.OutgoingEvent
import com.fiap.mechanicalhub.orders.application.ports.CustomerRepository
import com.fiap.mechanicalhub.orders.application.ports.InboxPort
import com.fiap.mechanicalhub.orders.application.ports.OutboxPort
import com.fiap.mechanicalhub.orders.application.ports.ProcessedCommand
import com.fiap.mechanicalhub.orders.application.ports.RepairRepository
import com.fiap.mechanicalhub.orders.application.ports.ServiceOrderFilter
import com.fiap.mechanicalhub.orders.application.ports.ServiceOrderRepository
import com.fiap.mechanicalhub.orders.application.ports.VehicleRepository
import com.fiap.mechanicalhub.orders.domain.model.Customer
import com.fiap.mechanicalhub.orders.domain.model.Repair
import com.fiap.mechanicalhub.orders.domain.model.ServiceOrder
import com.fiap.mechanicalhub.orders.domain.model.Vehicle
import org.springframework.dao.OptimisticLockingFailureException
import java.util.UUID

/** Repositorio em memoria com o mesmo controle otimista do adapter JPA. */
class FakeServiceOrderRepository(
    vararg initial: ServiceOrder,
) : ServiceOrderRepository {
    private val orders = initial.associateBy { it.id }.toMutableMap()
    private var sequence = 0L

    override fun findById(id: UUID): ServiceOrder? = orders[id]

    override fun findByOrderNumber(orderNumber: String): ServiceOrder? = orders.values.firstOrNull { it.orderNumber == orderNumber }

    override fun search(filter: ServiceOrderFilter): List<ServiceOrder> =
        orders.values.filter { order ->
            (filter.status == null || order.status == filter.status) &&
                (filter.customerId == null || order.customerId == filter.customerId) &&
                (filter.responsibleUserId == null || order.responsibleUserId == filter.responsibleUserId)
        }

    override fun save(order: ServiceOrder): ServiceOrder {
        val stored = orders[order.id]
        if (stored != null && stored.version != order.version) {
            throw OptimisticLockingFailureException("Ordem ${order.id} alterada por outra transacao")
        }
        val saved = if (stored == null) order else order.copy(version = order.version + 1)
        orders[order.id] = saved
        return saved
    }

    override fun nextOrderSequence(): Long = ++sequence
}

class FakeOutbox : OutboxPort {
    val events = mutableListOf<OutgoingEvent>()

    override fun append(event: OutgoingEvent) {
        events += event
    }
}

class FakeInbox : InboxPort {
    val entries = mutableMapOf<String, OutgoingEvent?>()

    override fun find(messageKey: String) = if (messageKey in entries) ProcessedCommand(entries[messageKey]) else null

    override fun register(
        messageKey: String,
        response: OutgoingEvent?,
    ) {
        entries[messageKey] = response
    }
}

class FakeCustomers(
    vararg customers: Customer = arrayOf(aCustomer()),
) : CustomerRepository {
    private val byId = customers.associateBy { it.id }

    override fun findById(id: UUID): Customer? = byId[id]
}

class FakeVehicles(
    vararg vehicles: Vehicle = arrayOf(aVehicle()),
) : VehicleRepository {
    private val byId = vehicles.associateBy { it.id }

    override fun findById(id: UUID): Vehicle? = byId[id]
}

class FakeRepairs(
    vararg repairs: Repair = arrayOf(aRepair()),
) : RepairRepository {
    private val byId = repairs.associateBy { it.id }

    override fun findAllActive(): List<Repair> = byId.values.filter { it.active }

    override fun findAllByIds(ids: Collection<UUID>): List<Repair> = ids.mapNotNull { byId[it] }
}

fun aCustomer(id: UUID = Seeds.CUSTOMER_CARLOS): Customer =
    Customer(
        id = id,
        name = "Carlos Silva",
        documentNumber = "12345678909",
        telephone = "11999990000",
        email = "carlos@example.com",
    )

fun aVehicle(
    id: UUID = Seeds.VEHICLE_GOL,
    customerId: UUID = Seeds.CUSTOMER_CARLOS,
): Vehicle = Vehicle(id = id, customerId = customerId, licensePlate = "BRA2E19", brand = "Volkswagen", model = "Gol")

fun aRepair(
    id: UUID = Seeds.OIL_CHANGE,
    name: String = "Troca de oleo",
    active: Boolean = true,
): Repair = Repair(id = id, name = name, description = null, active = active)
