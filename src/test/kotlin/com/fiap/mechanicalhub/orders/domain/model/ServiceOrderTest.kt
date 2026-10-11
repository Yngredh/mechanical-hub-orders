package com.fiap.mechanicalhub.orders.domain.model

import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.APPROVED
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.AWAITING_PAYMENT
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.CANCELED
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.DELIVERED
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.FINISHED
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.IN_DIAGNOSIS
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.IN_PROGRESS
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.PREPARING
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.READY_FOR_EXECUTION
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.REJECTED
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.WAITING_APPROVAL
import com.fiap.mechanicalhub.orders.domain.enums.TaskStatusEnum
import com.fiap.mechanicalhub.orders.domain.exceptions.InvalidOrderRequestException
import com.fiap.mechanicalhub.orders.domain.exceptions.InvalidOrderTransitionException
import com.fiap.mechanicalhub.orders.domain.exceptions.OrderRuleViolationException
import com.fiap.mechanicalhub.orders.fixtures.FIXED_NOW
import com.fiap.mechanicalhub.orders.fixtures.Seeds
import com.fiap.mechanicalhub.orders.fixtures.aServiceOrder
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal
import java.util.UUID
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ServiceOrderTest {
    @Nested
    inner class Open {
        @Test
        fun `given repairs when opening then the order starts in diagnosis with pending tasks`() {
            val data = newOrderData(repairIds = listOf(Seeds.OIL_CHANGE, Seeds.BRAKES, Seeds.OIL_CHANGE))

            val order = ServiceOrder.open(data, UUID::randomUUID)

            assertEquals(IN_DIAGNOSIS, order.status)
            assertEquals(listOf(Seeds.OIL_CHANGE, Seeds.BRAKES), order.repairIds)
            assertTrue(order.tasks.all { it.status == TaskStatusEnum.PENDING })
        }

        @Test
        fun `given no repairs when opening then it is rejected`() {
            val data = newOrderData(repairIds = emptyList())

            assertFailsWith<InvalidOrderRequestException> { ServiceOrder.open(data, UUID::randomUUID) }
        }
    }

    @Nested
    inner class Transitions {
        @ParameterizedTest(name = "{0} --{1}--> {2}")
        @MethodSource("com.fiap.mechanicalhub.orders.domain.model.ServiceOrderTest#validTransitions")
        fun `given the expected origin when transitioning then the status changes`(
            origin: OrderStatusEnum,
            action: Action,
            expected: OrderStatusEnum,
        ) {
            val order = aServiceOrder(status = origin, taskStatuses = listOf(TaskStatusEnum.FINISHED))

            val result = action.apply(order)

            assertEquals(expected, result.status)
        }

        @ParameterizedTest(name = "{1} from {0} is rejected")
        @MethodSource("com.fiap.mechanicalhub.orders.domain.model.ServiceOrderTest#invalidTransitions")
        fun `given another origin when transitioning then it throws an invalid transition`(
            origin: OrderStatusEnum,
            action: Action,
        ) {
            val order = aServiceOrder(status = origin, taskStatuses = listOf(TaskStatusEnum.FINISHED))

            assertFailsWith<InvalidOrderTransitionException> { action.apply(order) }
        }
    }

    @Nested
    inner class Rules {
        @Test
        fun `given no repairs when finishing diagnosis then it is rejected`() {
            val order = aServiceOrder(status = IN_DIAGNOSIS, taskStatuses = emptyList())

            assertFailsWith<OrderRuleViolationException> { order.finishDiagnosis() }
        }

        @Test
        fun `given stock pending when requesting approval then it is rejected`() {
            val order = aServiceOrder(status = PREPARING, hasStockPending = true)

            assertFailsWith<OrderRuleViolationException> {
                order.requestApproval(UUID.randomUUID(), BigDecimal.TEN, "hash")
            }
        }

        @Test
        fun `given stock pending when starting execution then it is rejected`() {
            val order = aServiceOrder(status = READY_FOR_EXECUTION, hasStockPending = true)

            assertFailsWith<OrderRuleViolationException> { order.startExecution() }
        }

        @Test
        fun `given an unfinished repair when finishing then it is rejected`() {
            val order = aServiceOrder(status = IN_PROGRESS, taskStatuses = listOf(TaskStatusEnum.FINISHED, TaskStatusEnum.STARTED))

            assertFailsWith<OrderRuleViolationException> { order.finish(FIXED_NOW) }
        }

        @Test
        fun `given approval data when requesting approval then budget, saga and token hash are recorded`() {
            val transactionId = UUID.randomUUID()
            val order = aServiceOrder(status = PREPARING)

            val result = order.requestApproval(transactionId, BigDecimal("308.10"), "hash")

            assertEquals(
                Triple(BigDecimal("308.10"), transactionId, "hash"),
                Triple(result.budget, result.sagaTransactionId, result.approvalTokenHash),
            )
        }

        @Test
        fun `given an order in preparation when marking stock pending then flag and saga are recorded`() {
            val transactionId = UUID.randomUUID()
            val order = aServiceOrder(status = PREPARING)

            val result = order.markStockPending(transactionId)

            assertEquals(true to transactionId, result.hasStockPending to result.sagaTransactionId)
        }

        @Test
        fun `given a pending order when clearing stock pending then the flag is removed`() {
            val order = aServiceOrder(status = PREPARING, hasStockPending = true)

            val result = order.clearStockPending()

            assertEquals(false, result.hasStockPending)
        }

        @Test
        fun `given an order outside preparation when marking stock pending then it is rejected`() {
            val order = aServiceOrder(status = WAITING_APPROVAL)

            assertFailsWith<InvalidOrderTransitionException> { order.markStockPending(UUID.randomUUID()) }
        }

        @Test
        fun `given a finished order when delivering then the delivery time is recorded`() {
            val order = aServiceOrder(status = FINISHED)

            val result = order.deliver(FIXED_NOW)

            assertEquals(FIXED_NOW, result.deliveredAt)
        }
    }

    @Nested
    inner class Repairs {
        @Test
        fun `given an order in diagnosis when adding repairs then only new ones are added`() {
            val order = ServiceOrder.open(newOrderData(repairIds = listOf(Seeds.OIL_CHANGE)), UUID::randomUUID)

            val result = order.addRepairs(listOf(Seeds.OIL_CHANGE, Seeds.BRAKES), UUID::randomUUID)

            assertEquals(listOf(Seeds.OIL_CHANGE, Seeds.BRAKES), result.repairIds)
        }

        @Test
        fun `given an order after diagnosis when adding repairs then it is rejected`() {
            val order = aServiceOrder(status = PREPARING)

            assertFailsWith<InvalidOrderTransitionException> { order.addRepairs(listOf(Seeds.BRAKES), UUID::randomUUID) }
        }

        @Test
        fun `given an order in progress when starting a repair then the task is started`() {
            val order = aServiceOrder(status = IN_PROGRESS)
            val repairId = order.repairIds.single()

            val result = order.updateTaskStatus(repairId, TaskStatusEnum.STARTED, FIXED_NOW)

            assertEquals(TaskStatusEnum.STARTED to FIXED_NOW, result.tasks.single().let { it.status to it.startedAt })
        }

        @Test
        fun `given a started repair when finishing it then the finish time is recorded`() {
            val order = aServiceOrder(status = IN_PROGRESS, taskStatuses = listOf(TaskStatusEnum.STARTED))
            val repairId = order.repairIds.single()

            val result = order.updateTaskStatus(repairId, TaskStatusEnum.FINISHED, FIXED_NOW)

            assertEquals(FIXED_NOW, result.tasks.single().finishedAt)
        }

        @Test
        fun `given a pending repair when finishing it directly then it is rejected`() {
            val order = aServiceOrder(status = IN_PROGRESS)
            val repairId = order.repairIds.single()

            assertFailsWith<OrderRuleViolationException> { order.updateTaskStatus(repairId, TaskStatusEnum.FINISHED, FIXED_NOW) }
        }

        @Test
        fun `given an order not in progress when updating a repair then it is rejected`() {
            val order = aServiceOrder(status = READY_FOR_EXECUTION)
            val repairId = order.repairIds.single()

            assertFailsWith<InvalidOrderTransitionException> { order.updateTaskStatus(repairId, TaskStatusEnum.STARTED, FIXED_NOW) }
        }

        @Test
        fun `given a repair outside the order when updating it then it is rejected`() {
            val order = aServiceOrder(status = IN_PROGRESS)

            assertFailsWith<InvalidOrderRequestException> { order.updateTaskStatus(Seeds.BRAKES, TaskStatusEnum.STARTED, FIXED_NOW) }
        }
    }

    enum class Action(
        val apply: (ServiceOrder) -> ServiceOrder,
    ) {
        FINISH_DIAGNOSIS({ it.finishDiagnosis() }),
        REQUEST_APPROVAL({ it.requestApproval(UUID.randomUUID(), BigDecimal.ONE, "hash") }),
        APPROVE({ it.approve() }),
        REJECT({ it.reject() }),
        AWAIT_PAYMENT({ it.awaitPayment() }),
        RELEASE_FOR_EXECUTION({ it.releaseForExecution() }),
        CANCEL({ it.cancel() }),
        START_EXECUTION({ it.startExecution() }),
        FINISH({ it.finish(FIXED_NOW) }),
        DELIVER({ it.deliver(FIXED_NOW) }),
    }

    companion object {
        private val transitions =
            mapOf(
                Action.FINISH_DIAGNOSIS to (IN_DIAGNOSIS to PREPARING),
                Action.REQUEST_APPROVAL to (PREPARING to WAITING_APPROVAL),
                Action.APPROVE to (WAITING_APPROVAL to APPROVED),
                Action.REJECT to (WAITING_APPROVAL to REJECTED),
                Action.AWAIT_PAYMENT to (APPROVED to AWAITING_PAYMENT),
                Action.RELEASE_FOR_EXECUTION to (AWAITING_PAYMENT to READY_FOR_EXECUTION),
                Action.CANCEL to (AWAITING_PAYMENT to CANCELED),
                Action.START_EXECUTION to (READY_FOR_EXECUTION to IN_PROGRESS),
                Action.FINISH to (IN_PROGRESS to FINISHED),
                Action.DELIVER to (FINISHED to DELIVERED),
            )

        @JvmStatic
        fun validTransitions(): Stream<Arguments> =
            transitions.entries.stream().map { (action, fromTo) -> Arguments.of(fromTo.first, action, fromTo.second) }

        @JvmStatic
        fun invalidTransitions(): Stream<Arguments> =
            transitions.entries.stream().flatMap { (action, fromTo) ->
                OrderStatusEnum.entries
                    .filter { it != fromTo.first }
                    .stream()
                    .map { origin -> Arguments.of(origin, action) }
            }

        private fun newOrderData(repairIds: List<UUID>) =
            NewServiceOrder(
                id = UUID.randomUUID(),
                orderNumber = "OS-202610-0001",
                customerId = Seeds.CUSTOMER_CARLOS,
                vehicleId = Seeds.VEHICLE_GOL,
                createdByUserId = Seeds.MECHANIC,
                requestDescription = "Revisao",
                repairIds = repairIds,
                openedAt = FIXED_NOW,
            )
    }
}
