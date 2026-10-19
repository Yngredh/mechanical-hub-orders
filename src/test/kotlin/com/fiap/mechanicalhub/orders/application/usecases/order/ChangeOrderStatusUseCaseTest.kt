package com.fiap.mechanicalhub.orders.application.usecases.order

import com.fiap.mechanicalhub.orders.application.events.OutgoingEvent
import com.fiap.mechanicalhub.orders.application.events.RepairReference
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum
import com.fiap.mechanicalhub.orders.domain.enums.TaskStatusEnum
import com.fiap.mechanicalhub.orders.domain.exceptions.InvalidOrderTransitionException
import com.fiap.mechanicalhub.orders.domain.exceptions.OrderRuleViolationException
import com.fiap.mechanicalhub.orders.domain.exceptions.ServiceOrderNotFoundException
import com.fiap.mechanicalhub.orders.fixtures.FIXED_NOW
import com.fiap.mechanicalhub.orders.fixtures.FakeOutbox
import com.fiap.mechanicalhub.orders.fixtures.FakeServiceOrderRepository
import com.fiap.mechanicalhub.orders.fixtures.aServiceOrder
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChangeOrderStatusUseCaseTest {
    private val outbox = FakeOutbox()
    private val clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC)

    private fun useCaseWith(orders: FakeServiceOrderRepository) = ChangeOrderStatusUseCase(orders, outbox, clock)

    @Test
    fun `given an order in diagnosis when moving to preparing then diagnosisCompleted carries its repairs`() {
        val order = aServiceOrder(status = OrderStatusEnum.IN_DIAGNOSIS)

        val changed = useCaseWith(FakeServiceOrderRepository(order)).execute(order.id, OrderStatusEnum.PREPARING)

        assertEquals(OrderStatusEnum.PREPARING, changed.status)
        assertEquals(
            listOf<OutgoingEvent>(OutgoingEvent.DiagnosisCompleted(order.id, order.repairIds.map(::RepairReference))),
            outbox.events,
        )
    }

    @Test
    fun `given an order ready for execution when moving to in progress then nothing is published`() {
        val order = aServiceOrder(status = OrderStatusEnum.READY_FOR_EXECUTION)

        val changed = useCaseWith(FakeServiceOrderRepository(order)).execute(order.id, OrderStatusEnum.IN_PROGRESS)

        assertEquals(OrderStatusEnum.IN_PROGRESS to emptyList<OutgoingEvent>(), changed.status to outbox.events)
    }

    @Test
    fun `given all repairs finished when finishing then orderFinished carries the saga transaction`() {
        val transactionId = UUID.randomUUID()
        val order =
            aServiceOrder(
                status = OrderStatusEnum.IN_PROGRESS,
                taskStatuses = listOf(TaskStatusEnum.FINISHED),
                sagaTransactionId = transactionId,
            )

        val changed = useCaseWith(FakeServiceOrderRepository(order)).execute(order.id, OrderStatusEnum.FINISHED)

        assertEquals(FIXED_NOW, changed.completedAt)
        assertEquals(listOf<OutgoingEvent>(OutgoingEvent.OrderFinished(transactionId, order.id)), outbox.events)
    }

    @Test
    fun `given an order without saga when finishing then it is a rule violation and nothing is saved`() {
        val order = aServiceOrder(status = OrderStatusEnum.IN_PROGRESS, taskStatuses = listOf(TaskStatusEnum.FINISHED))
        val orders = FakeServiceOrderRepository(order)

        assertFailsWith<OrderRuleViolationException> { useCaseWith(orders).execute(order.id, OrderStatusEnum.FINISHED) }
        assertEquals(OrderStatusEnum.IN_PROGRESS, orders.findById(order.id)?.status)
    }

    @Test
    fun `given a finished order when delivering then the delivery time is recorded without events`() {
        val order = aServiceOrder(status = OrderStatusEnum.FINISHED)

        val changed = useCaseWith(FakeServiceOrderRepository(order)).execute(order.id, OrderStatusEnum.DELIVERED)

        assertEquals<Pair<Instant?, List<OutgoingEvent>>>(FIXED_NOW to emptyList(), changed.deliveredAt to outbox.events)
    }

    @ParameterizedTest
    @EnumSource(
        OrderStatusEnum::class,
        names = ["PREPARING", "IN_PROGRESS", "FINISHED", "DELIVERED"],
        mode = EnumSource.Mode.EXCLUDE,
    )
    fun `given a status reserved to the saga when the mechanic requests it then it is an invalid transition`(target: OrderStatusEnum) {
        val order = aServiceOrder(status = OrderStatusEnum.IN_DIAGNOSIS)

        assertFailsWith<InvalidOrderTransitionException> { useCaseWith(FakeServiceOrderRepository(order)).execute(order.id, target) }
    }

    @Test
    fun `given an unknown order when changing status then it is not found`() {
        assertFailsWith<ServiceOrderNotFoundException> {
            useCaseWith(FakeServiceOrderRepository()).execute(UUID.randomUUID(), OrderStatusEnum.PREPARING)
        }
    }
}
