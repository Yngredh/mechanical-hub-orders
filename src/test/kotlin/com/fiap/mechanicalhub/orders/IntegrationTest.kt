package com.fiap.mechanicalhub.orders

import com.fiap.mechanicalhub.orders.application.ports.ServiceOrderRepository
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum
import com.fiap.mechanicalhub.orders.domain.model.NewServiceOrder
import com.fiap.mechanicalhub.orders.domain.model.ServiceOrder
import com.fiap.mechanicalhub.orders.fixtures.Seeds
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Base dos testes de integracao: contexto completo com PostgreSQL em Testcontainers. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
abstract class IntegrationTest {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    protected lateinit var serviceOrderRepository: ServiceOrderRepository

    @Autowired
    protected lateinit var transactionTemplate: TransactionTemplate

    protected fun MockHttpServletRequestDsl.asMechanic() {
        header("x-user-id", Seeds.MECHANIC.toString())
        header("x-user-role", "MECHANICAL")
        header("x-user-name", "Mecanico Padrao")
    }

    /** Ordem gravada direto pelo repositorio, ja no status pedido. */
    protected fun givenOrder(
        status: OrderStatusEnum = OrderStatusEnum.IN_DIAGNOSIS,
        approvalTokenHash: String? = null,
        sagaTransactionId: UUID? = null,
        hasStockPending: Boolean = false,
        mutate: (ServiceOrder) -> ServiceOrder = { it },
    ): ServiceOrder {
        val opened =
            ServiceOrder.open(
                NewServiceOrder(
                    id = UUID.randomUUID(),
                    orderNumber = "OS-TEST-${UUID.randomUUID().toString().take(ORDER_SUFFIX)}",
                    customerId = Seeds.CUSTOMER_CARLOS,
                    vehicleId = Seeds.VEHICLE_GOL,
                    createdByUserId = Seeds.MECHANIC,
                    requestDescription = "Teste de integracao",
                    repairIds = listOf(Seeds.OIL_CHANGE),
                    openedAt = Instant.now(),
                ),
                UUID::randomUUID,
            )
        val order =
            mutate(
                opened.copy(
                    status = status,
                    approvalTokenHash = approvalTokenHash,
                    sagaTransactionId = sagaTransactionId,
                    hasStockPending = hasStockPending,
                    budget = approvalTokenHash?.let { BigDecimal("308.10") },
                ),
            )
        return requireNotNull(transactionTemplate.execute { serviceOrderRepository.save(order) })
    }

    protected fun outboxTypes(orderId: UUID): List<String> =
        jdbcTemplate
            .queryForList(
                "SELECT event_type FROM outbox_events WHERE aggregate_id = ? ORDER BY sequence",
                String::class.java,
                orderId,
            ).filterNotNull()

    protected fun outboxPayloads(orderId: UUID): List<String> =
        jdbcTemplate
            .queryForList(
                "SELECT payload::text FROM outbox_events WHERE aggregate_id = ? ORDER BY sequence",
                String::class.java,
                orderId,
            ).filterNotNull()

    private companion object {
        const val ORDER_SUFFIX = 8
    }
}
