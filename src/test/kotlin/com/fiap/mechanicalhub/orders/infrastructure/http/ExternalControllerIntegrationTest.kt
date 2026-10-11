package com.fiap.mechanicalhub.orders.infrastructure.http

import com.fiap.mechanicalhub.orders.IntegrationTest
import com.fiap.mechanicalhub.orders.domain.approval.ApprovalToken
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.security.SecureRandom
import java.util.UUID
import kotlin.test.assertEquals

class ExternalControllerIntegrationTest : IntegrationTest() {
    private val token = ApprovalToken.generate(SecureRandom())

    @Test
    fun `given a valid token when reading the order then summary, status and budget are returned`() {
        val order = givenOrder(status = OrderStatusEnum.WAITING_APPROVAL, approvalTokenHash = token.hash())

        val result = mockMvc.get("/mechanical-hub/service-orders/${order.orderNumber}") { header(TOKEN, token.value) }

        result.andExpect {
            status { isOk() }
            jsonPath("$.status.code") { value("WAITING_APPROVAL") }
            jsonPath("$.budget") { value(308.10) }
            jsonPath("$.vehicleLicensePlate") { value("BRA2E19") }
        }
    }

    @Test
    fun `given no token when reading the order then it is not found`() {
        val order = givenOrder(status = OrderStatusEnum.WAITING_APPROVAL, approvalTokenHash = token.hash())

        val result = mockMvc.get("/mechanical-hub/service-orders/${order.orderNumber}")

        result.andExpect { status { isNotFound() } }
    }

    @Test
    fun `given a wrong token when approving then it is not found and nothing is published`() {
        val order =
            givenOrder(status = OrderStatusEnum.WAITING_APPROVAL, approvalTokenHash = token.hash(), sagaTransactionId = UUID.randomUUID())

        val result = mockMvc.post("/mechanical-hub/service-orders/${order.id}/approve") { header(TOKEN, "outro-token") }

        result.andExpect { status { isNotFound() } }
        assertEquals(emptyList(), outboxTypes(order.id))
    }

    @Test
    fun `given a valid token when approving then the order is approved and orderApproved goes to the outbox`() {
        val order =
            givenOrder(status = OrderStatusEnum.WAITING_APPROVAL, approvalTokenHash = token.hash(), sagaTransactionId = UUID.randomUUID())

        val result = mockMvc.post("/mechanical-hub/service-orders/${order.id}/approve") { header(TOKEN, token.value) }

        result.andExpect { status { isNoContent() } }
        assertEquals(OrderStatusEnum.APPROVED, serviceOrderRepository.findById(order.id)?.status)
        assertEquals(listOf("orderApproved"), outboxTypes(order.id))
    }

    @Test
    fun `given an approved order when approving again then it conflicts without a new event`() {
        val order = givenOrder(status = OrderStatusEnum.APPROVED, approvalTokenHash = token.hash(), sagaTransactionId = UUID.randomUUID())

        val result = mockMvc.post("/mechanical-hub/service-orders/${order.id}/approve") { header(TOKEN, token.value) }

        result.andExpect { status { isConflict() } }
        assertEquals(emptyList(), outboxTypes(order.id))
    }

    @Test
    fun `given a valid token when rejecting then the order is rejected and orderRejected goes to the outbox`() {
        val order =
            givenOrder(status = OrderStatusEnum.WAITING_APPROVAL, approvalTokenHash = token.hash(), sagaTransactionId = UUID.randomUUID())

        val result = mockMvc.post("/mechanical-hub/service-orders/${order.id}/reject") { header(TOKEN, token.value) }

        result.andExpect { status { isNoContent() } }
        assertEquals(listOf("orderRejected"), outboxTypes(order.id))
    }

    private companion object {
        const val TOKEN = "X-Approval-Token"
    }
}
