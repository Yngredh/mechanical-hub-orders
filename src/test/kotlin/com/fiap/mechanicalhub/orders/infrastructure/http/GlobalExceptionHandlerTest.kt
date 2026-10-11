package com.fiap.mechanicalhub.orders.infrastructure.http

import com.fiap.mechanicalhub.orders.infrastructure.http.error.GlobalExceptionHandler
import org.junit.jupiter.api.Test
import org.springframework.dao.QueryTimeoutException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.CannotCreateTransactionException
import java.util.UUID
import kotlin.test.assertEquals

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    @Test
    fun `given a transient database failure when handling then it answers 503`() {
        val exception = QueryTimeoutException("lock timeout")

        val problem = handler.unavailable(exception)

        assertEquals(503, problem.status)
    }

    @Test
    fun `given the database is unreachable when handling then it answers 503`() {
        val exception = CannotCreateTransactionException("connection refused")

        val problem = handler.unavailable(exception)

        assertEquals(503, problem.status)
    }

    @Test
    fun `given a version conflict when handling then it answers 409`() {
        val exception = ObjectOptimisticLockingFailureException("ServiceOrderEntity", UUID.randomUUID())

        val problem = handler.versionConflict(exception)

        assertEquals(409, problem.status)
    }
}
