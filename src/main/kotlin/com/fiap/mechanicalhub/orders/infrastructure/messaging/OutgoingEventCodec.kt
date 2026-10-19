package com.fiap.mechanicalhub.orders.infrastructure.messaging

import com.fiap.mechanicalhub.orders.application.events.OutgoingEvent
import com.fiap.mechanicalhub.orders.application.events.RepairReference
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

/**
 * Serializa a resposta guardada em `inbox_events.response_payload` (sem
 * `eventId`: a regravacao no outbox gera um envelope novo, com outro eventId,
 * e a guarda de passo do orquestrador descarta a repeticao).
 */
@Component
class OutgoingEventCodec(
    private val jsonMapper: JsonMapper,
) {
    fun encode(event: OutgoingEvent): String =
        jsonMapper.writeValueAsString(
            linkedMapOf("type" to event.type, "transactionId" to event.transactionId, "orderId" to event.orderId) +
                when (event) {
                    is OutgoingEvent.DiagnosisCompleted -> mapOf("repairs" to event.repairs.map { mapOf("repairId" to it.repairId) })

                    is OutgoingEvent.OrderApproved,
                    is OutgoingEvent.OrderRejected,
                    is OutgoingEvent.OrderFinished,
                    is OutgoingEvent.OrderCanceled,
                    -> emptyMap()
                },
        )

    fun decode(json: String): OutgoingEvent {
        val node = jsonMapper.readTree(json)
        val orderId = node.uuid("orderId")
        return when (val type = node["type"].asString()) {
            "diagnosisCompleted" -> {
                OutgoingEvent.DiagnosisCompleted(
                    orderId,
                    node["repairs"]
                        .iterator()
                        .asSequence()
                        .map {
                            RepairReference(it.uuid("repairId"))
                        }.toList(),
                )
            }

            "orderApproved" -> {
                OutgoingEvent.OrderApproved(node.uuid("transactionId"), orderId)
            }

            "orderRejected" -> {
                OutgoingEvent.OrderRejected(node.uuid("transactionId"), orderId)
            }

            "orderFinished" -> {
                OutgoingEvent.OrderFinished(node.uuid("transactionId"), orderId)
            }

            "orderCanceled" -> {
                OutgoingEvent.OrderCanceled(node.uuid("transactionId"), orderId)
            }

            else -> {
                error("Resposta desconhecida no inbox: $type")
            }
        }
    }

    private fun JsonNode.uuid(field: String): UUID = UUID.fromString(this[field].asString())
}
