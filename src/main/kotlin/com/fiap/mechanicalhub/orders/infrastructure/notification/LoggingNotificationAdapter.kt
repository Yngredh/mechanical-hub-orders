package com.fiap.mechanicalhub.orders.infrastructure.notification

import com.fiap.mechanicalhub.orders.application.notifications.Notification
import com.fiap.mechanicalhub.orders.application.ports.NotificationPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** Registra a notificacao em log; substituido pelos canais reais na Etapa 7. */
@Component
class LoggingNotificationAdapter : NotificationPort {
    override fun send(notification: Notification) {
        log.info("Notificacao {} da ordem {}", notification.javaClass.simpleName, notification.orderId)
    }

    private companion object {
        val log = LoggerFactory.getLogger(LoggingNotificationAdapter::class.java)
    }
}
