package com.fiap.mechanicalhub.orders.infrastructure.observability

import org.slf4j.MDC

/** Chaves de correlacao do log JSON (logback-spring.xml). */
object LogContext {
    const val ORDER_ID = "orderId"
    const val TRANSACTION_ID = "transactionId"
    const val EVENT_ID = "eventId"

    /** Executa `block` com as chaves no MDC e remove todas ao final (valores nulos so sao limpos). */
    fun <T> with(
        vararg entries: Pair<String, Any?>,
        block: () -> T,
    ): T {
        val present = entries.mapNotNull { (key, value) -> value?.let { key to it.toString() } }
        present.forEach { (key, value) -> MDC.put(key, value) }
        return try {
            block()
        } finally {
            entries.forEach { (key, _) -> MDC.remove(key) }
        }
    }

    fun put(
        key: String,
        value: Any?,
    ) {
        value?.let { MDC.put(key, it.toString()) }
    }
}
