package com.fiap.mechanicalhub.orders.application.usecases.order

import com.fiap.mechanicalhub.orders.application.ports.ServiceOrderFilter
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum
import com.fiap.mechanicalhub.orders.domain.enums.TaskStatusEnum
import com.fiap.mechanicalhub.orders.domain.exceptions.RepairNotFoundException
import com.fiap.mechanicalhub.orders.fixtures.FIXED_NOW
import com.fiap.mechanicalhub.orders.fixtures.FakeRepairs
import com.fiap.mechanicalhub.orders.fixtures.FakeServiceOrderRepository
import com.fiap.mechanicalhub.orders.fixtures.Seeds
import com.fiap.mechanicalhub.orders.fixtures.aRepair
import com.fiap.mechanicalhub.orders.fixtures.aServiceOrder
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OrderRepairsUseCasesTest {
    private val clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
    private val repairs = FakeRepairs(aRepair(id = Seeds.OIL_CHANGE), aRepair(id = Seeds.BRAKES, name = "Freios", active = false))

    @Test
    fun `given an order in diagnosis when adding an active repair then it is saved as pending`() {
        val order = aServiceOrder(status = OrderStatusEnum.IN_DIAGNOSIS)
        val orders = FakeServiceOrderRepository(order)

        val changed = AddRepairsUseCase(orders, repairs).execute(order.id, listOf(Seeds.OIL_CHANGE))

        assertEquals(order.repairIds + Seeds.OIL_CHANGE, changed.repairIds)
        assertEquals(TaskStatusEnum.PENDING, changed.tasks.last().status)
    }

    @Test
    fun `given an inactive repair when adding then it is not found`() {
        val order = aServiceOrder(status = OrderStatusEnum.IN_DIAGNOSIS)

        assertFailsWith<RepairNotFoundException> {
            AddRepairsUseCase(FakeServiceOrderRepository(order), repairs).execute(order.id, listOf(Seeds.BRAKES))
        }
    }

    @Test
    fun `given an order in progress when starting a repair then it is saved with the start time`() {
        val order = aServiceOrder(status = OrderStatusEnum.IN_PROGRESS)
        val repairId = order.repairIds.single()

        val changed = UpdateTaskStatusUseCase(FakeServiceOrderRepository(order), clock).execute(order.id, repairId, TaskStatusEnum.STARTED)

        assertEquals(TaskStatusEnum.STARTED to FIXED_NOW, changed.tasks.single().let { it.status to it.startedAt })
    }

    @Test
    fun `given orders of several statuses when searching by status then only the matching ones are returned`() {
        val pending = aServiceOrder(status = OrderStatusEnum.PREPARING, hasStockPending = true)
        val orders = FakeServiceOrderRepository(pending, aServiceOrder(status = OrderStatusEnum.IN_DIAGNOSIS))

        val found = QueryServiceOrdersUseCase(orders, repairs).search(ServiceOrderFilter(status = OrderStatusEnum.PREPARING))

        assertEquals(listOf(pending), found.map { it.order })
    }
}
