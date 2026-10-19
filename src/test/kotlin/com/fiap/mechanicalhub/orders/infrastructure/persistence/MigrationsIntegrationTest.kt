package com.fiap.mechanicalhub.orders.infrastructure.persistence

import com.fiap.mechanicalhub.orders.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class MigrationsIntegrationTest {
    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Test
    fun `given the migrations when reading services then the catalog matches seed_catalog`() {
        val repairIds = jdbcTemplate.queryForList("SELECT id::text FROM services WHERE active ORDER BY id", String::class.java)

        assertEquals(
            listOf(
                "03d94934-6201-449b-b8db-490546c35d34",
                "192c4db5-61b3-4cf7-b02b-54e948a9ce5d",
                "b9090e29-c8f8-498f-b536-bb70cb69113e",
                "eeff2222-2222-2222-2222-000000000001",
            ),
            repairIds,
        )
    }

    @Test
    fun `given the auth role when running the login query then it reads the seeded employee`() {
        val profile =
            transactionTemplate.execute {
                jdbcTemplate.execute("SET LOCAL ROLE mechanical_hub_auth")
                jdbcTemplate.queryForMap(LOGIN_QUERY, "11144477735")["profile"]
            }

        assertEquals("MECHANICAL", profile)
    }

    @Test
    fun `given the monolith seeds when counting customers and vehicles then all are present`() {
        val vehiclesWithCustomer =
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vehicles v JOIN customers c ON c.id = v.customer_id",
                Int::class.java,
            )

        assertEquals(4, vehiclesWithCustomer)
    }

    private companion object {
        // Mesma consulta de sql-user-repository.ts (mechanical-hub-auth).
        const val LOGIN_QUERY = """
            SELECT u.id, u.name, u.document_number, u.password_hash, u.deleted_at, p.name AS profile
              FROM users u
              JOIN profiles p ON p.id = u.profile_id
             WHERE u.document_number = ?
             LIMIT 1
        """
    }
}
