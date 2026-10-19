package com.fiap.mechanicalhub.orders

import com.fiap.mechanicalhub.orders.application.notifications.Notification
import com.fiap.mechanicalhub.orders.application.ports.NotificationPort
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum
import com.fiap.mechanicalhub.orders.fixtures.Seeds
import io.awspring.cloud.sqs.operations.SqsTemplate
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals

/**
 * Fluxos da saga do ponto de vista do orders (TD_finishedOS, TD_PendenciaEstoque,
 * TD_budgetRefused e TD_paymentFailed). O teste faz o papel do orquestrador:
 * le os eventos publicados pelo relay e envia os comandos seguintes pela fila
 * real (LocalStack), na ordem da tabela de mechanical-hub-orchestrator-spec.md §3.
 */
@Import(SagaFlowIntegrationTest.RecordingNotifications::class)
class SagaFlowIntegrationTest : IntegrationTest() {
    @Autowired
    private lateinit var sqsTemplate: SqsTemplate

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    @Autowired
    private lateinit var notifications: RecordingNotificationPort

    private val transactionId = UUID.randomUUID()

    @Test
    fun `given the happy path when the saga runs then the order goes from diagnosis to delivered`() {
        val orderId = openAndFinishDiagnosis()
        val approvalToken = sendApprovalAndCaptureToken(orderId)
        decide(orderId, approvalToken, "approve")
        sendCommand(orderId, "sendPaymentLink", "SEND_PAYMENT_LINK", ""","paymentLink":"https://mp.example/pay/1"""")
        sendCommand(orderId, "releaseOrder", "RELEASE_ORDER")
        awaitStatus(orderId, OrderStatusEnum.READY_FOR_EXECUTION)

        changeStatus(orderId, OrderStatusEnum.IN_PROGRESS)
        updateRepair(orderId, "STARTED")
        updateRepair(orderId, "FINISHED")
        changeStatus(orderId, OrderStatusEnum.FINISHED)
        changeStatus(orderId, OrderStatusEnum.DELIVERED)

        assertEquals(OrderStatusEnum.DELIVERED, serviceOrderRepository.findById(orderId)?.status)
        awaitPublished(orderId, listOf("diagnosisCompleted", "orderApproved", "orderFinished"))
        assertEquals(
            listOf("BudgetReady", "PaymentLinkReady", "OrderReleased"),
            notifications.sent(orderId).map { it::class.simpleName },
        )
    }

    @Test
    fun `given insufficient stock when the saga runs then the order waits for stock and only then goes to approval`() {
        val orderId = openAndFinishDiagnosis()

        sendCommand(orderId, "markStockPending", "MARK_STOCK_PENDING")
        await atMost TIMEOUT untilAsserted { assertEquals(true, serviceOrderRepository.findById(orderId)?.hasStockPending) }
        mockMvc
            .get(
                "/orders?status=PREPARING",
            ) { asMechanic() }
            .andExpect { jsonPath("$[?(@.id=='$orderId')].hasStockPending") { value(true) } }
        sendCommand(orderId, "clearStockPending", "CLEAR_STOCK_PENDING")
        sendApprovalAndCaptureToken(orderId)

        val saved = serviceOrderRepository.findById(orderId)
        assertEquals(OrderStatusEnum.WAITING_APPROVAL to false, saved?.status to saved?.hasStockPending)
    }

    @Test
    fun `given the customer refuses the budget when the saga runs then the order is rejected and orderRejected is published`() {
        val orderId = openAndFinishDiagnosis()
        val approvalToken = sendApprovalAndCaptureToken(orderId)

        decide(orderId, approvalToken, "reject")

        assertEquals(OrderStatusEnum.REJECTED, serviceOrderRepository.findById(orderId)?.status)
        awaitPublished(orderId, listOf("diagnosisCompleted", "orderRejected"))
    }

    @Test
    fun `given the payment is not confirmed when the saga runs then the order is canceled and orderCanceled is published`() {
        val orderId = openAndFinishDiagnosis()
        val approvalToken = sendApprovalAndCaptureToken(orderId)
        decide(orderId, approvalToken, "approve")
        sendCommand(orderId, "sendPaymentLink", "SEND_PAYMENT_LINK", ""","paymentLink":"https://mp.example/pay/2"""")

        sendCommand(orderId, "cancelOrder", "CANCEL_ORDER", ""","reason":"PAYMENT_NOT_CONFIRMED"""")

        awaitStatus(orderId, OrderStatusEnum.CANCELED)
        awaitPublished(orderId, listOf("diagnosisCompleted", "orderApproved", "orderCanceled"))
    }

    // ── Passos do mecanico e do cliente (HTTP) ──────────────────────────────

    private fun openAndFinishDiagnosis(): UUID {
        val created =
            mockMvc
                .post("/orders") {
                    asMechanic()
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"customerId":"${Seeds.CUSTOMER_CARLOS}","vehicleId":"${Seeds.VEHICLE_GOL}",
                           "serviceIds":["${Seeds.OIL_CHANGE}"],"requestDescription":"Fluxo da saga"}"""
                }.andExpect { status { isCreated() } }
                .andReturn()
        val orderId = UUID.fromString(jsonMapper.readTree(created.response.contentAsString)["id"].asString())
        changeStatus(orderId, OrderStatusEnum.PREPARING)
        awaitPublished(orderId, listOf("diagnosisCompleted"))
        return orderId
    }

    private fun changeStatus(
        orderId: UUID,
        status: OrderStatusEnum,
    ) {
        mockMvc
            .patch("/orders/$orderId/status") {
                asMechanic()
                contentType = MediaType.APPLICATION_JSON
                content = """{"status":"$status"}"""
            }.andExpect { status { isOk() } }
    }

    private fun updateRepair(
        orderId: UUID,
        status: String,
    ) {
        mockMvc
            .post("/orders/status") {
                asMechanic()
                contentType = MediaType.APPLICATION_JSON
                content = """{"orderId":"$orderId","repairId":"${Seeds.OIL_CHANGE}","status":"$status"}"""
            }.andExpect { status { isOk() } }
    }

    /** `sendApproval` do orquestrador; devolve o token que chegou ao cliente na notificacao. */
    private fun sendApprovalAndCaptureToken(orderId: UUID): String {
        sendCommand(orderId, "sendApproval", "SEND_APPROVAL", ""","budgetId":"${UUID.randomUUID()}","orderCost":308.10""")
        val budget =
            await atMost TIMEOUT untilNotNull {
                notifications.sent(orderId).filterIsInstance<Notification.BudgetReady>().firstOrNull()
            }
        mockMvc
            .get("/mechanical-hub/service-orders/${budget.orderNumber}") { header(APPROVAL_TOKEN, budget.approvalToken.value) }
            .andExpect {
                status { isOk() }
                jsonPath("$.status.code") { value("WAITING_APPROVAL") }
            }
        return budget.approvalToken.value
    }

    private fun decide(
        orderId: UUID,
        approvalToken: String,
        decision: String,
    ) {
        mockMvc
            .post("/mechanical-hub/service-orders/$orderId/$decision") { header(APPROVAL_TOKEN, approvalToken) }
            .andExpect { status { isNoContent() } }
    }

    // ── Papel do orquestrador (SQS) ─────────────────────────────────────────

    private fun sendCommand(
        orderId: UUID,
        type: String,
        step: String,
        extra: String = "",
    ) {
        val eventId = UUID.randomUUID()
        val body =
            """{"eventId":"$eventId","transactionId":"$transactionId","orderId":"$orderId","type":"$type","version":1,
               "timestamp":"2026-10-11T12:00:00Z","idempotencyKey":"$transactionId:$step"$extra}"""
        sqsTemplate.send<String> {
            it
                .queue(COMMANDS_QUEUE)
                .payload(body)
                .messageGroupId(orderId.toString())
                .messageDeduplicationId(eventId.toString())
        }
    }

    private fun awaitStatus(
        orderId: UUID,
        status: OrderStatusEnum,
    ) {
        await atMost TIMEOUT untilAsserted { assertEquals(status, serviceOrderRepository.findById(orderId)?.status) }
    }

    /** Eventos gravados no outbox, na ordem, e ja enviados pelo relay a fila do orquestrador. */
    private fun awaitPublished(
        orderId: UUID,
        types: List<String>,
    ) {
        await atMost TIMEOUT untilAsserted {
            assertEquals(types.map { it to "SENT" }, outboxStatuses(orderId))
        }
    }

    private fun outboxStatuses(orderId: UUID): List<Pair<String, String>> =
        jdbcTemplate.query(
            "SELECT event_type, status FROM outbox_events WHERE aggregate_id = ? ORDER BY sequence",
            { rs, _ -> rs.getString("event_type") to rs.getString("status") },
            orderId,
        )

    /** Guarda as notificacoes em vez de envia-las, para o teste ler o token de aprovacao. */
    class RecordingNotificationPort : NotificationPort {
        private val notifications = CopyOnWriteArrayList<Notification>()

        override fun send(notification: Notification) {
            notifications += notification
        }

        fun sent(orderId: UUID): List<Notification> = notifications.filter { it.orderId == orderId }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class RecordingNotifications {
        @Bean
        @Primary
        fun recordingNotificationPort() = RecordingNotificationPort()
    }

    private companion object {
        const val COMMANDS_QUEUE = "mechanical-hub-orders-commands.fifo"
        const val APPROVAL_TOKEN = "X-Approval-Token"
        val TIMEOUT: Duration = Duration.ofSeconds(20)
    }
}
