package com.fiap.mechanicalhub.orders.infrastructure.messaging.commands

import com.fiap.mechanicalhub.orders.domain.exceptions.DomainException
import org.springframework.dao.DuplicateKeyException
import org.springframework.dao.NonTransientDataAccessException

enum class ErrorClass { TRANSIENT, PERMANENT }

/**
 * Classificacao de failure_handling.md §1. Permanente: fora do contrato, regra
 * de negocio ou transicao invalida, erro de dado nao retentavel. Qualquer outra
 * falha (banco fora, deadlock, lock_timeout, conflito de versao, timeout) e
 * tratada como transitoria: se nao se resolver, a redrive policy leva a DLQ.
 */
object CommandErrorClassifier {
    fun classify(error: Throwable): ErrorClass =
        when {
            // Duas entregas do mesmo comando em corrida: a retentativa cai na deduplicacao.
            error.isCausedBy<DuplicateKeyException>() -> ErrorClass.TRANSIENT

            error.isCausedBy<InvalidMessageException>() -> ErrorClass.PERMANENT

            error.isCausedBy<DomainException>() -> ErrorClass.PERMANENT

            error.isCausedBy<NonTransientDataAccessException>() -> ErrorClass.PERMANENT

            else -> ErrorClass.TRANSIENT
        }

    private inline fun <reified T : Throwable> Throwable.isCausedBy(): Boolean = generateSequence(this) { it.cause }.any { it is T }
}
