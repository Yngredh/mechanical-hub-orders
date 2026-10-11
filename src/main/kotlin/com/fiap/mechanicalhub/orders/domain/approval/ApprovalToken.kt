package com.fiap.mechanicalhub.orders.domain.approval

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Token de aprovacao do cliente (TD_finishedOS.md passo 11): 32 bytes do
 * `SecureRandom` em base64url. Somente o SHA-256 e persistido.
 */
@JvmInline
value class ApprovalToken(
    val value: String,
) {
    fun hash(): String = sha256Hex(value)

    override fun toString(): String = "ApprovalToken(***)"

    companion object {
        private const val TOKEN_BYTES = 32
        private val encoder = Base64.getUrlEncoder().withoutPadding()

        fun generate(random: SecureRandom): ApprovalToken {
            val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
            return ApprovalToken(encoder.encodeToString(bytes))
        }
    }
}

/** Compara o token recebido com o hash salvo em tempo constante. */
fun String.matchesApprovalToken(rawToken: String?): Boolean {
    if (rawToken.isNullOrBlank()) return false
    return MessageDigest.isEqual(toByteArray(), sha256Hex(rawToken).toByteArray())
}

private fun sha256Hex(value: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString(separator = "") { "%02x".format(it) }
