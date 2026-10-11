package com.fiap.mechanicalhub.orders.infrastructure.messaging.commands

import com.fiap.mechanicalhub.orders.application.notifications.Notification
import com.fiap.mechanicalhub.orders.application.usecases.commands.CommandOutcome
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** O que o listener faz com a mensagem depois do processamento. */
sealed interface CommandDecision {
    /** COMMIT feito (ou duplicata): apagar a mensagem e, se houver, notificar. */
    data class Acknowledge(
        val notification: Notification?,
    ) : CommandDecision

    /** Erro transitorio: nao apagar; nova tentativa apos `delaySeconds`. */
    data class Retry(
        val delaySeconds: Int,
        val error: Throwable,
    ) : CommandDecision

    /** Erro permanente: enviar a DLQ e apagar da fila de origem. */
    data class DeadLetter(
        val error: Throwable,
    ) : CommandDecision
}

/**
 * Parse → caso de uso → decisao, isolado do SDK para ser testado sem SQS.
 * Backoff bloqueante por `ApproximateReceiveCount`: 1 s, 2 s, 4 s, 8 s, 8 s
 * (failure_handling.md §2.2); o 7o recebimento vai sozinho para a DLQ.
 */
@Component
class CommandMessageProcessor(
    private val parser: OrderCommandParser,
    private val dispatcher: OrderCommandDispatcher,
) {
    fun process(
        body: String,
        receiveCount: Int,
    ): CommandDecision =
        runCatching { dispatcher.dispatch(parser.parse(body)) }
            .fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        is CommandOutcome.Applied -> {
                            CommandDecision.Acknowledge(outcome.notification)
                        }

                        CommandOutcome.Duplicate -> {
                            log.info("Comando duplicado ignorado (inbox_events)")
                            CommandDecision.Acknowledge(notification = null)
                        }
                    }
                },
                onFailure = { error ->
                    when (CommandErrorClassifier.classify(error)) {
                        ErrorClass.TRANSIENT -> CommandDecision.Retry(backoffSeconds(receiveCount), error)
                        ErrorClass.PERMANENT -> CommandDecision.DeadLetter(error)
                    }
                },
            )

    private companion object {
        val BACKOFF_SECONDS = listOf(1, 2, 4, 8, 8)
        val log = LoggerFactory.getLogger(CommandMessageProcessor::class.java)

        fun backoffSeconds(receiveCount: Int): Int = BACKOFF_SECONDS[(receiveCount - 1).coerceIn(BACKOFF_SECONDS.indices)]
    }
}
