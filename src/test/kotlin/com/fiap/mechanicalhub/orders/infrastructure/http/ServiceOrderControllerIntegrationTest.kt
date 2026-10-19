package com.fiap.mechanicalhub.orders.infrastructure.http

import com.fiap.mechanicalhub.orders.IntegrationTest
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum
import com.fiap.mechanicalhub.orders.domain.enums.TaskStatusEnum
import com.fiap.mechanicalhub.orders.fixtures.Seeds
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceOrderControllerIntegrationTest : IntegrationTest() {
    @Test
    fun `given customer, vehicle and repairs when opening then the order starts in diagnosis`() {
        val body = """
            {"customerId":"${Seeds.CUSTOMER_CARLOS}","vehicleId":"${Seeds.VEHICLE_GOL}",
             "serviceIds":["${Seeds.OIL_CHANGE}"],"requestDescription":"Troca de oleo"}
        """

        val result =
            mockMvc.post("/orders") {
                asMechanic()
                contentType = MediaType.APPLICATION_JSON
                content = body
            }

        result.andExpect {
            status { isCreated() }
            jsonPath("$.status.code") { value("IN_DIAGNOSIS") }
            jsonPath("$.status.displayName") { value("Em diagnóstico") }
            jsonPath("$.repairs[0].status.code") { value("PENDING") }
            jsonPath("$.orderNumber") { isNotEmpty() }
        }
    }

    @Test
    fun `given no gateway headers when opening then it is unauthorized`() {
        val result = mockMvc.post("/orders") { contentType = MediaType.APPLICATION_JSON }

        result.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `given a vehicle of another customer when opening then it is unprocessable`() {
        val body = """
            {"customerId":"${Seeds.CUSTOMER_CARLOS}","vehicleId":"${Seeds.VEHICLE_KWID}",
             "serviceIds":["${Seeds.OIL_CHANGE}"],"requestDescription":"Revisao"}
        """

        val result =
            mockMvc.post("/orders") {
                asMechanic()
                contentType = MediaType.APPLICATION_JSON
                content = body
            }

        result.andExpect { status { isUnprocessableContent() } }
    }

    @Test
    fun `given an unknown repair when opening then it is not found`() {
        val body = """
            {"customerId":"${Seeds.CUSTOMER_CARLOS}","vehicleId":"${Seeds.VEHICLE_GOL}",
             "serviceIds":["${UUID.randomUUID()}"],"requestDescription":"Revisao"}
        """

        val result =
            mockMvc.post("/orders") {
                asMechanic()
                contentType = MediaType.APPLICATION_JSON
                content = body
            }

        result.andExpect { status { isNotFound() } }
    }

    @Test
    fun `given an empty repair list when opening then it is a bad request`() {
        val body = """
            {"customerId":"${Seeds.CUSTOMER_CARLOS}","vehicleId":"${Seeds.VEHICLE_GOL}",
             "serviceIds":[],"requestDescription":"Revisao"}
        """

        val result =
            mockMvc.post("/orders") {
                asMechanic()
                contentType = MediaType.APPLICATION_JSON
                content = body
            }

        result.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `given an order in diagnosis when adding repairs then they are listed`() {
        val order = givenOrder()

        val result =
            mockMvc.patch("/orders/${order.id}/repairs") {
                asMechanic()
                contentType = MediaType.APPLICATION_JSON
                content = """{"repairs":[{"repairId":"${Seeds.BRAKES}"}]}"""
            }

        result.andExpect {
            status { isOk() }
            jsonPath("$.repairs.length()") { value(2) }
        }
    }

    @Test
    fun `given an order in diagnosis when finishing diagnosis then diagnosisCompleted goes to the outbox`() {
        val order = givenOrder()

        mockMvc
            .patch("/orders/${order.id}/status") {
                asMechanic()
                contentType = MediaType.APPLICATION_JSON
                content = """{"status":"PREPARING"}"""
            }.andExpect { status { isOk() } }

        assertEquals(listOf("diagnosisCompleted"), outboxTypes(order.id))
        assertTrue(outboxPayloads(order.id).single().contains(Seeds.OIL_CHANGE.toString()))
    }

    @Test
    fun `given an order already preparing when finishing diagnosis again then it conflicts and publishes nothing`() {
        val order = givenOrder(status = OrderStatusEnum.PREPARING)

        val result =
            mockMvc.patch("/orders/${order.id}/status") {
                asMechanic()
                contentType = MediaType.APPLICATION_JSON
                content = """{"status":"PREPARING"}"""
            }

        result.andExpect { status { isConflict() } }
        assertEquals(emptyList(), outboxTypes(order.id))
    }

    @Test
    fun `given a status reserved to the saga when the mechanic requests it then it conflicts`() {
        val order = givenOrder(status = OrderStatusEnum.WAITING_APPROVAL)

        val result =
            mockMvc.patch("/orders/${order.id}/status") {
                asMechanic()
                contentType = MediaType.APPLICATION_JSON
                content = """{"status":"APPROVED"}"""
            }

        result.andExpect { status { isConflict() } }
    }

    @Test
    fun `given all repairs finished when finishing the order then orderFinished goes to the outbox`() {
        val transactionId = UUID.randomUUID()
        val order =
            givenOrder(status = OrderStatusEnum.IN_PROGRESS, sagaTransactionId = transactionId) { order ->
                order.copy(tasks = order.tasks.map { it.copy(status = TaskStatusEnum.FINISHED) })
            }

        mockMvc
            .patch("/orders/${order.id}/status") {
                asMechanic()
                contentType = MediaType.APPLICATION_JSON
                content = """{"status":"FINISHED"}"""
            }.andExpect { status { isOk() } }

        assertEquals(listOf("orderFinished"), outboxTypes(order.id))
    }

    @Test
    fun `given an order in progress when starting a repair then the repair is started`() {
        val order = givenOrder(status = OrderStatusEnum.IN_PROGRESS)

        val result =
            mockMvc.post("/orders/status") {
                asMechanic()
                contentType = MediaType.APPLICATION_JSON
                content = """{"orderId":"${order.id}","repairId":"${Seeds.OIL_CHANGE}","status":"STARTED"}"""
            }

        result.andExpect {
            status { isOk() }
            jsonPath("$.repairs[0].status.code") { value("STARTED") }
        }
    }

    @Test
    fun `given a stock pending order when filtering by status then it shows the pending flag`() {
        val order = givenOrder(status = OrderStatusEnum.PREPARING, hasStockPending = true)

        val result = mockMvc.get("/orders?status=PREPARING&customerId=${Seeds.CUSTOMER_CARLOS}") { asMechanic() }

        result.andExpect {
            status { isOk() }
            jsonPath("$[?(@.id == '${order.id}')].hasStockPending") { value(true) }
        }
    }

    @Test
    fun `given an unknown order when reading it then it is not found`() {
        val result = mockMvc.get("/orders/${UUID.randomUUID()}") { asMechanic() }

        result.andExpect { status { isNotFound() } }
    }

    @Test
    fun `given the catalog when listing services then the four seeded repairs are returned`() {
        val result = mockMvc.get("/services") { asMechanic() }

        result.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(4) }
        }
    }
}
