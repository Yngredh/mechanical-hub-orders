package com.fiap.mechanicalhub.orders.infrastructure.notification.channels

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** Mensagem pronta para envio (mesmo formato do monolito: assunto + corpo). */
data class NotificationMessage(
    val subject: String,
    val body: String,
)

/** Destinatario com os contatos disponiveis (cliente ou funcionario). */
data class Recipient(
    val name: String,
    val email: String?,
    val telephone: String?,
)

/** Canal de envio. Pode lancar: quem chama trata a falha (so log). */
interface NotificationChannel {
    fun send(
        recipient: Recipient,
        message: NotificationMessage,
    )
}

/**
 * E-mail, reaproveitado do `EmailSender` do monolito: registra o envio em log.
 * A integracao com um provedor real de e-mail fica pendente (acesso externo).
 */
@Component
class EmailSender : NotificationChannel {
    override fun send(
        recipient: Recipient,
        message: NotificationMessage,
    ) {
        val email = recipient.email ?: return
        log.info("Enviando email para {} com assunto: {} e corpo: {}", email, message.subject, message.body)
    }

    private companion object {
        val log = LoggerFactory.getLogger(EmailSender::class.java)
    }
}

/**
 * WhatsApp, reaproveitado do `WhatsAppMessenger` do monolito: registra o envio
 * em log. A integracao com a API real do WhatsApp fica pendente.
 */
@Component
class WhatsAppMessenger : NotificationChannel {
    override fun send(
        recipient: Recipient,
        message: NotificationMessage,
    ) {
        val telephone = recipient.telephone ?: return
        log.info("Enviando WhatsApp para {}: {}", telephone, message.subject)
    }

    private companion object {
        val log = LoggerFactory.getLogger(WhatsAppMessenger::class.java)
    }
}
