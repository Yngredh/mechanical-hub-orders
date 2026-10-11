package com.fiap.mechanicalhub.orders.infrastructure.http

import com.fiap.mechanicalhub.orders.IntegrationTest
import com.fiap.mechanicalhub.orders.domain.approval.ApprovalToken
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum
import org.junit.jupiter.api.Test
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.test.web.servlet.post
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Aprovacao e recusa do mesmo orcamento ao mesmo tempo (TD_budgetRefused.md):
 * so uma decisao vence; a outra recebe 409 (transicao invalida ou `version`).
 */
class ConcurrentDecisionIntegrationTest : IntegrationTest() {
    private val token = ApprovalToken.generate(SecureRandom())

    @Test
    fun `given approve and reject at the same time when both arrive then exactly one decision wins and is published`() {
        val order =
            givenOrder(status = OrderStatusEnum.WAITING_APPROVAL, approvalTokenHash = token.hash(), sagaTransactionId = UUID.randomUUID())
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(DECISIONS.size)

        val statuses =
            executor.use { pool ->
                DECISIONS
                    .map { decision ->
                        CompletableFuture.supplyAsync({
                            start.await()
                            mockMvc
                                .post("/mechanical-hub/service-orders/${order.id}/$decision") { header(TOKEN, token.value) }
                                .andReturn()
                                .response.status
                        }, pool)
                    }.also { start.countDown() }
                    .map { it.join() }
            }

        val finalStatus = serviceOrderRepository.findById(order.id)?.status
        assertEquals(listOf(NO_CONTENT, CONFLICT), statuses.sorted())
        assertEquals(listOf(EVENT_BY_STATUS[finalStatus]), outboxTypes(order.id))
    }

    @Test
    fun `given a decision saved from a stale copy when saving then the version conflict is raised`() {
        val order =
            givenOrder(status = OrderStatusEnum.WAITING_APPROVAL, approvalTokenHash = token.hash(), sagaTransactionId = UUID.randomUUID())
        val stale = requireNotNull(serviceOrderRepository.findById(order.id))
        transactionTemplate.execute { serviceOrderRepository.save(stale.approve()) }

        assertFailsWith<OptimisticLockingFailureException> {
            transactionTemplate.execute { serviceOrderRepository.save(stale.reject()) }
        }
        assertEquals(OrderStatusEnum.APPROVED, serviceOrderRepository.findById(order.id)?.status)
    }

    private companion object {
        const val TOKEN = "X-Approval-Token"
        const val NO_CONTENT = 204
        const val CONFLICT = 409
        val DECISIONS = listOf("approve", "reject")
        val EVENT_BY_STATUS = mapOf(OrderStatusEnum.APPROVED to "orderApproved", OrderStatusEnum.REJECTED to "orderRejected")
    }
}
