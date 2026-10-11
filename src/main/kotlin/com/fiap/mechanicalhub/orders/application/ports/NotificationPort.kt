package com.fiap.mechanicalhub.orders.application.ports

import com.fiap.mechanicalhub.orders.application.notifications.Notification

/**
 * Envio de notificacoes, chamado apos o COMMIT. Implementacoes nao lancam:
 * falha no envio so gera log e nao desfaz o comando.
 */
fun interface NotificationPort {
    fun send(notification: Notification)
}
