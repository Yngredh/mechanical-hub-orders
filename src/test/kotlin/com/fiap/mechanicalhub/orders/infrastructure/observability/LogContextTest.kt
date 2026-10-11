package com.fiap.mechanicalhub.orders.infrastructure.observability

import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LogContextTest {
    @Test
    fun `given correlation ids when running a block then they are in the MDC only during it`() {
        val orderId = UUID.randomUUID()

        val inside = LogContext.with(LogContext.ORDER_ID to orderId, LogContext.EVENT_ID to null) { MDC.get(LogContext.ORDER_ID) }

        assertEquals(orderId.toString() to null, inside to MDC.get(LogContext.ORDER_ID))
    }

    @Test
    fun `given a key added inside the block when it ends then the declared key is cleared`() {
        LogContext.with(LogContext.TRANSACTION_ID to null) { LogContext.put(LogContext.TRANSACTION_ID, "tx-1") }

        val after = MDC.get(LogContext.TRANSACTION_ID)

        assertNull(after)
    }
}
