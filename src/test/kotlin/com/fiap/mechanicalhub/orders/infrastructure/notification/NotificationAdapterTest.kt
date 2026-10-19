package com.fiap.mechanicalhub.orders.infrastructure.notification

import com.fiap.mechanicalhub.orders.application.notifications.Notification
import com.fiap.mechanicalhub.orders.application.ports.CustomerRepository
import com.fiap.mechanicalhub.orders.domain.approval.ApprovalToken
import com.fiap.mechanicalhub.orders.domain.model.Customer
import com.fiap.mechanicalhub.orders.fixtures.Seeds
import com.fiap.mechanicalhub.orders.infrastructure.config.OrdersProperties
import com.fiap.mechanicalhub.orders.infrastructure.notification.channels.NotificationChannel
import com.fiap.mechanicalhub.orders.infrastructure.notification.channels.NotificationMessage
import com.fiap.mechanicalhub.orders.infrastructure.notification.channels.Recipient
import com.fiap.mechanicalhub.orders.infrastructure.persistence.entities.EmployeeEntity
import com.fiap.mechanicalhub.orders.infrastructure.persistence.repositories.EmployeeJpaRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationAdapterTest {
    private val customerRepository = mockk<CustomerRepository>()
    private val employeeRepository = mockk<EmployeeJpaRepository>()
    private val channel = RecordingChannel()
    private val orderId = UUID.randomUUID()
    private val properties =
        OrdersProperties(
            queues = OrdersProperties.Queues("commands", "commands-dlq", "events"),
            frontendBaseUrl = "https://app.mechanicalhub.com/",
        )

    private val carlos = Customer(Seeds.CUSTOMER_CARLOS, "Carlos Eduardo Mendes", "52998224725", "5511987453210", "carlos.mendes@gmail.com")

    @Test
    fun `given a budget ready when notifying then the customer receives the budget and the approval link`() {
        every { customerRepository.findById(Seeds.CUSTOMER_CARLOS) } returns carlos
        val token = ApprovalToken("token-abc")

        adapter(channel).send(Notification.BudgetReady(orderId, "OS-202610-0001", Seeds.CUSTOMER_CARLOS, BigDecimal("308.10"), token))

        val (recipient, message) = channel.sent.single()
        assertEquals("carlos.mendes@gmail.com", recipient.email)
        assertTrue(message.body.contains("https://app.mechanicalhub.com/orders/$orderId/approval?token=token-abc"))
        assertTrue(message.body.contains("308,10"))
    }

    @Test
    fun `given a payment link when notifying then the customer receives it`() {
        every { customerRepository.findById(Seeds.CUSTOMER_CARLOS) } returns carlos

        adapter(channel).send(Notification.PaymentLinkReady(orderId, "OS-1", Seeds.CUSTOMER_CARLOS, "https://mp/checkout/1"))

        assertTrue(
            channel.sent
                .single()
                .second.body
                .contains("https://mp/checkout/1"),
        )
    }

    @Test
    fun `given a released order when notifying then the responsible mechanic is notified`() {
        every { employeeRepository.findByIdAndDeletedAtIsNull(Seeds.MECHANIC) } returns
            EmployeeEntity(Seeds.MECHANIC, "Mecanico Padrao", "mecanico@mechanicalhub.com", null)

        adapter(channel).send(Notification.OrderReleased(orderId, "OS-1", Seeds.MECHANIC))

        assertEquals(
            "mecanico@mechanicalhub.com",
            channel.sent
                .single()
                .first.email,
        )
    }

    @Test
    fun `given a failing channel when notifying then the error is only logged and other channels still send`() {
        every { customerRepository.findById(Seeds.CUSTOMER_CARLOS) } returns carlos
        val failing =
            object : NotificationChannel {
                override fun send(
                    recipient: Recipient,
                    message: NotificationMessage,
                ): Unit = error("SMTP fora do ar")
            }

        adapter(failing, channel).send(Notification.PaymentLinkReady(orderId, "OS-1", Seeds.CUSTOMER_CARLOS, "https://mp/1"))

        assertEquals(1, channel.sent.size)
    }

    @Test
    fun `given the customer lookup fails when notifying then nothing is thrown`() {
        every { customerRepository.findById(any()) } throws IllegalStateException("banco fora")

        adapter(channel).send(Notification.PaymentLinkReady(orderId, "OS-1", Seeds.CUSTOMER_CARLOS, "https://mp/1"))

        assertEquals(0, channel.sent.size)
    }

    @Test
    fun `given an order without responsible mechanic when released then nobody is notified`() {
        adapter(channel).send(Notification.OrderReleased(orderId, "OS-1", responsibleUserId = null))

        assertEquals(0, channel.sent.size)
    }

    private fun adapter(vararg channels: NotificationChannel) =
        NotificationAdapter(customerRepository, employeeRepository, channels.toList(), properties)

    private class RecordingChannel : NotificationChannel {
        val sent = mutableListOf<Pair<Recipient, NotificationMessage>>()

        override fun send(
            recipient: Recipient,
            message: NotificationMessage,
        ) {
            sent += recipient to message
        }
    }
}
