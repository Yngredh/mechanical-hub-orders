package com.fiap.mechanicalhub.orders.infrastructure.http.error

import com.fiap.mechanicalhub.orders.domain.exceptions.InvalidOrderRequestException
import com.fiap.mechanicalhub.orders.domain.exceptions.InvalidOrderTransitionException
import com.fiap.mechanicalhub.orders.domain.exceptions.NotFoundException
import com.fiap.mechanicalhub.orders.domain.exceptions.OrderRuleViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.dao.TransientDataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.jdbc.CannotGetJdbcConnectionException
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * Erros padronizados em ProblemDetail (RFC 9457), conforme failure_handling.md §6:
 * transicao invalida ou conflito de versao → 409; falha transitoria de banco → 503.
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(NotFoundException::class)
    fun notFound(exception: NotFoundException) = problem(HttpStatus.NOT_FOUND, "Nao encontrado", exception.message)

    @ExceptionHandler(InvalidOrderTransitionException::class, OrderRuleViolationException::class)
    fun conflict(exception: RuntimeException) = problem(HttpStatus.CONFLICT, "Transicao nao permitida", exception.message)

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun versionConflict(exception: OptimisticLockingFailureException): ProblemDetail {
        log.info("Conflito de versao: {}", exception.message)
        return problem(HttpStatus.CONFLICT, "Conflito de versao", "A ordem foi alterada por outra requisicao; recarregue e tente novamente")
    }

    @ExceptionHandler(InvalidOrderRequestException::class)
    fun unprocessable(exception: InvalidOrderRequestException) =
        problem(HttpStatus.UNPROCESSABLE_CONTENT, "Requisicao inconsistente", exception.message)

    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
    )
    fun badRequest(exception: Exception) = problem(HttpStatus.BAD_REQUEST, "Requisicao invalida", exception.message)

    @ExceptionHandler(
        TransientDataAccessException::class,
        DataAccessResourceFailureException::class,
        CannotGetJdbcConnectionException::class,
        CannotCreateTransactionException::class,
    )
    fun unavailable(exception: Exception): ProblemDetail {
        log.warn("Falha transitoria de banco: {}", exception.message)
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Servico indisponivel", "Falha transitoria; tente novamente")
    }

    private fun problem(
        status: HttpStatus,
        title: String,
        detail: String?,
    ): ProblemDetail = ProblemDetail.forStatusAndDetail(status, detail.orEmpty()).apply { this.title = title }

    private companion object {
        val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
