package com.fiap.mechanicalhub.orders.application.usecases.order

import com.fiap.mechanicalhub.orders.application.ports.ServiceOrderFilter
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum
import com.fiap.mechanicalhub.orders.domain.enums.TaskStatusEnum
import com.fiap.mechanicalhub.orders.domain.exceptions.CustomerNotFoundException
import com.fiap.mechanicalhub.orders.domain.exceptions.InvalidOrderRequestException
import com.fiap.mechanicalhub.orders.domain.exceptions.RepairNotFoundException
import com.fiap.mechanicalhub.orders.domain.exceptions.VehicleNotFoundException
import com.fiap.mechanicalhub.orders.fixtures.FIXED_NOW
import com.fiap.mechanicalhub.orders.fixtures.FakeCustomers
import com.fiap.mechanicalhub.orders.fixtures.FakeRepairs
import com.fiap.mechanicalhub.orders.fixtures.FakeServiceOrderRepository
import com.fiap.mechanicalhub.orders.fixtures.FakeVehicles
import com.fiap.mechanicalhub.orders.fixtures.Seeds
import com.fiap.mechanicalhub.orders.fixtures.aRepair
import com.fiap.mechanicalhub.orders.fixtures.aVehicle
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpenServiceOrderUseCaseTest {
    private val orders = FakeServiceOrderRepository()
    private val clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC)

    private fun useCase(
        vehicles: FakeVehicles = FakeVehicles(),
        repairs: FakeRepairs = FakeRepairs(),
    ) = OpenServiceOrderUseCase(orders, FakeCustomers(), vehicles, repairs, clock)

    private fun command(
        vehicleId: UUID = Seeds.VEHICLE_GOL,
        customerId: UUID = Seeds.CUSTOMER_CARLOS,
        repairIds: List<UUID> = listOf(Seeds.OIL_CHANGE),
    ) = OpenServiceOrderCommand(
        customerId = customerId,
        vehicleId = vehicleId,
        repairIds = repairIds,
        requestDescription = "Barulho no motor",
        userId = Seeds.MECHANIC,
    )

    @Test
    fun `given customer, vehicle and active repair when opening then the order is saved in diagnosis with pending tasks`() {
        val opened = useCase().execute(command())

        assertEquals(OrderStatusEnum.IN_DIAGNOSIS, opened.status)
        assertEquals(listOf(Seeds.OIL_CHANGE to TaskStatusEnum.PENDING), opened.tasks.map { it.repairId to it.status })
        assertEquals(opened, orders.findById(opened.id))
    }

    @Test
    fun `given the clock and the sequence when opening then the number has year, month and padded sequence`() {
        val opened = useCase().execute(command())

        assertEquals("OS-202610-0001", opened.orderNumber)
    }

    @Test
    fun `given the logged user when opening then it is the creator and the responsible`() {
        val opened = useCase().execute(command())

        assertEquals(Seeds.MECHANIC to Seeds.MECHANIC, opened.createdByUserId to opened.responsibleUserId)
    }

    @Test
    fun `given an unknown customer when opening then it is not found`() {
        assertFailsWith<CustomerNotFoundException> { useCase().execute(command(customerId = UUID.randomUUID())) }
    }

    @Test
    fun `given an unknown vehicle when opening then it is not found`() {
        assertFailsWith<VehicleNotFoundException> { useCase().execute(command(vehicleId = UUID.randomUUID())) }
    }

    @Test
    fun `given a vehicle of another customer when opening then the request is inconsistent`() {
        val vehicles = FakeVehicles(aVehicle(id = Seeds.VEHICLE_KWID, customerId = UUID.randomUUID()))

        assertFailsWith<InvalidOrderRequestException> { useCase(vehicles = vehicles).execute(command(vehicleId = Seeds.VEHICLE_KWID)) }
    }

    @Test
    fun `given an inactive repair when opening then it is not found and nothing is saved`() {
        val repairs = FakeRepairs(aRepair(id = Seeds.BRAKES, active = false))

        assertFailsWith<RepairNotFoundException> { useCase(repairs = repairs).execute(command(repairIds = listOf(Seeds.BRAKES))) }
        assertEquals(emptyList(), orders.search(ServiceOrderFilter()))
    }
}
