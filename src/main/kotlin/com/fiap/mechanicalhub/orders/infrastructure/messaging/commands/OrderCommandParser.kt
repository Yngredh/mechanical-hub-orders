package com.fiap.mechanicalhub.orders.infrastructure.messaging.commands

import com.fiap.mechanicalhub.orders.application.commands.OrderCommand
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.util.UUID

/** Mensagem fora do contrato: erro permanente, vai direto para a DLQ. */
class InvalidMessageException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Converte o envelope JSON (infrastructure.md §4) no comando da aplicacao.
 * Campos do envelope e do payload ficam no mesmo nivel.
 */
@Component
class OrderCommandParser(
    private val jsonMapper: JsonMapper,
) {
    fun parse(body: String): OrderCommand {
        val node =
            try {
                jsonMapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(body)
            } catch (exception: JacksonException) {
                throw InvalidMessageException("Corpo nao e um JSON valido", exception)
            }
        return try {
            toCommand(node)
        } catch (exception: IllegalArgumentException) {
            throw InvalidMessageException("Mensagem fora do contrato: ${exception.message}", exception)
        }
    }

    private fun toCommand(node: JsonNode): OrderCommand {
        val transactionId = node.uuid("transactionId")
        val orderId = node.uuid("orderId")
        val key = node.text("idempotencyKey")
        return when (val type = node.text("type")) {
            "sendApproval" -> OrderCommand.SendApproval(transactionId, orderId, key, node.uuid("budgetId"), node.decimal("orderCost"))
            "markStockPending" -> OrderCommand.MarkStockPending(transactionId, orderId, key)
            "clearStockPending" -> OrderCommand.ClearStockPending(transactionId, orderId, key)
            "sendPaymentLink" -> OrderCommand.SendPaymentLink(transactionId, orderId, key, node.text("paymentLink"))
            "releaseOrder" -> OrderCommand.ReleaseOrder(transactionId, orderId, key)
            "cancelOrder" -> OrderCommand.CancelOrder(transactionId, orderId, key, node.text("reason"))
            else -> throw IllegalArgumentException("tipo de comando desconhecido: $type")
        }
    }

    private fun JsonNode.text(field: String): String {
        val value = get(field)?.takeIf { it.isString }?.asString()
        require(!value.isNullOrBlank()) { "campo obrigatorio ausente: $field" }
        return value
    }

    private fun JsonNode.uuid(field: String): UUID = UUID.fromString(text(field))

    private fun JsonNode.decimal(field: String): BigDecimal {
        val value = get(field)
        require(value != null && value.isNumber) { "campo numerico obrigatorio ausente: $field" }
        return value.decimalValue()
    }
}
