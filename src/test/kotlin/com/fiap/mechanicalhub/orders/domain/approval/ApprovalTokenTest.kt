package com.fiap.mechanicalhub.orders.domain.approval

import org.junit.jupiter.api.Test
import java.security.SecureRandom
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ApprovalTokenTest {
    private val random = SecureRandom()

    @Test
    fun `given a secure random when generating then the token has 32 bytes in base64url`() {
        val token = ApprovalToken.generate(random)

        assertEquals(32, Base64.getUrlDecoder().decode(token.value).size)
    }

    @Test
    fun `given two generations when comparing then the tokens differ`() {
        val first = ApprovalToken.generate(random)

        val second = ApprovalToken.generate(random)

        assertNotEquals(first, second)
    }

    @Test
    fun `given a token when hashing then it is the sha-256 hex digest`() {
        val token = ApprovalToken("abc")

        val hash = token.hash()

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash)
    }

    @Test
    fun `given the stored hash when the same token arrives then it matches`() {
        val token = ApprovalToken.generate(random)

        val matches = token.hash().matchesApprovalToken(token.value)

        assertTrue(matches)
    }

    @Test
    fun `given the stored hash when another token arrives then it does not match`() {
        val storedHash = ApprovalToken.generate(random).hash()

        val matches = storedHash.matchesApprovalToken(ApprovalToken.generate(random).value)

        assertFalse(matches)
    }

    @Test
    fun `given the stored hash when no token arrives then it does not match`() {
        val storedHash = ApprovalToken.generate(random).hash()

        val matches = storedHash.matchesApprovalToken(null)

        assertFalse(matches)
    }

    @Test
    fun `given a token when printing then the value is masked`() {
        val token = ApprovalToken("secret")

        val printed = token.toString()

        assertFalse("secret" in printed)
    }
}
