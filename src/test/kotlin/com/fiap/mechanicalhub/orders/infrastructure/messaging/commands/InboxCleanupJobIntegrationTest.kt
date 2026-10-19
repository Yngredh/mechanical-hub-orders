package com.fiap.mechanicalhub.orders.infrastructure.messaging.commands

import com.fiap.mechanicalhub.orders.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals

class InboxCleanupJobIntegrationTest : IntegrationTest() {
    @Autowired
    private lateinit var inboxCleanupJob: InboxCleanupJob

    @Test
    fun `given inbox rows older and newer than 30 days when the job runs then only the old ones are deleted`() {
        val oldKey = "${UUID.randomUUID()}:OLD"
        val recentKey = "${UUID.randomUUID()}:RECENT"
        jdbcTemplate.update("INSERT INTO inbox_events (message_key, processed_at) VALUES (?, now() - interval '31 days')", oldKey)
        jdbcTemplate.update("INSERT INTO inbox_events (message_key, processed_at) VALUES (?, now() - interval '29 days')", recentKey)

        inboxCleanupJob.purgeExpired()

        val remaining =
            jdbcTemplate.queryForList(
                "SELECT message_key FROM inbox_events WHERE message_key IN (?, ?)",
                String::class.java,
                oldKey,
                recentKey,
            )
        assertEquals(listOf(recentKey), remaining)
    }
}
