package com.fiap.mechanicalhub.orders.application.usecases.customer

import com.fiap.mechanicalhub.orders.application.events.OutgoingEvent
import com.fiap.mechanicalhub.orders.domain.approval.ApprovalToken
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum
import com.fiap.mechanicalhub.orders.domain.exceptions.InvalidOrderTransitionException
import com.fiap.mechanicalhub.orders.domain.exceptions.ServiceOrderNotFoundException
import com.fiap.mechanicalhub.orders.fixtures.FakeCustomers
import com.fiap.mechanicalhub.orders.fixtures.FakeOutbox
import com.fiap.mechanicalhub.orders.fixtures.FakeRepairs
import com.fiap.mechanicalhub.orders.fixtures.FakeServiceOrderRepository
import com.fiap.mechanicalhub.orders.fixtures.FakeVehicles
import com.fiap.mechanicalhub.orders.fixtures.aRepair
import com.fiap.mechanicalhub.orders.fixtures.aServiceOrder
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.security.SecureRandom
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CustomerOrderUseCasesTest {
    private val token = ApprovalToken.generate(SecureRandom())
    private val transactionId = UUID.randomUUID()
    private val outbox = FakeOutbox()
    private val waiting =
        aServiceOrder(status = OrderStatusEnum.WAITING_APPROVAL, sagaTransactionId = transactionId, approvalTokenHash = token.hash())
            .copy(budget = BigDecimal("308.10"))
    private val orders = FakeServiceOrderRepository(waiting)

    @Test
    fun `given the approval token when approving then the order is approved and orderApproved is recorded`() {
        ApproveOrderUseCase(orders, outbox).execute(waiting.id, token.value)

        assertEquals(OrderStatusEnum.APPROVED, orders.findById(waiting.id)?.status)
        assertEquals(listOf<OutgoingEvent>(OutgoingEvent.OrderApproved(transactionId, waiting.id)), outbox.events)
    }

    @Test
    fun `given the approval token when rejecting then the order is rejected and orderRejected is recorded`() {
        RejectOrderUseCase(orders, outbox).execute(waiting.id, token.value)

        assertEquals(OrderStatusEnum.REJECTED, orders.findById(waiting.id)?.status)
        assertEquals(listOf<OutgoingEvent>(OutgoingEvent.OrderRejected(transactionId, waiting.id)), outbox.events)
    }

    @Test
    fun `given a wrong token when approving then the order is reported as not found`() {
        assertFailsWith<ServiceOrderNotFoundException> { ApproveOrderUseCase(orders, outbox).execute(waiting.id, "outro-token") }
    }

    @Test
    fun `given an order still without budget when approving then it is reported as not found`() {
        val inDiagnosis = aServiceOrder(status = OrderStatusEnum.IN_DIAGNOSIS)

        assertFailsWith<ServiceOrderNotFoundException> {
            ApproveOrderUseCase(FakeServiceOrderRepository(inDiagnosis), outbox).execute(inDiagnosis.id, token.value)
        }
    }

    @Test
    fun `given an unknown order when rejecting then it is not found`() {
        assertFailsWith<ServiceOrderNotFoundException> { RejectOrderUseCase(orders, outbox).execute(UUID.randomUUID(), token.value) }
    }

    @Test
    fun `given an already rejected order when approving then it is an invalid transition and nothing is recorded`() {
        val rejected = waiting.copy(status = OrderStatusEnum.REJECTED)

        assertFailsWith<InvalidOrderTransitionException> {
            ApproveOrderUseCase(FakeServiceOrderRepository(rejected), outbox).execute(rejected.id, token.value)
        }
        assertEquals(emptyList(), outbox.events)
    }

    @Test
    fun `given the approval token when reading by number then the summary shows vehicle, budget and repairs`() {
        val repairs = FakeRepairs(aRepair(id = waiting.repairIds.single(), name = "Troca de oleo"))
        val useCase = GetCustomerOrderUseCase(orders, FakeCustomers(), FakeVehicles(), repairs)

        val view = useCase.execute(waiting.orderNumber, token.value)

        assertEquals(
            listOf("Carlos Silva", "BRA2E19", "308.10", "Troca de oleo"),
            listOf(view.customerName, view.vehicleLicensePlate, view.budget?.toPlainString(), view.services.single()),
        )
    }
}
