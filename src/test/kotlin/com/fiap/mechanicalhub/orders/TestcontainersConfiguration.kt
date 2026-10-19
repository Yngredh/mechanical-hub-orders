package com.fiap.mechanicalhub.orders

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer =
        PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withInitScript("db/test-init.sql")

    /** Mesmas filas FIFO + DLQs do docker-compose, criadas antes do contexto subir. */
    @Bean
    @ServiceConnection
    fun localStackContainer(): LocalStackContainer =
        LocalStackContainer(DockerImageName.parse("localstack/localstack:4.4"))
            .withServices("sqs")
            .withCopyFileToContainer(MountableFile.forHostPath("docker/localstack/init-sqs.sh", OWNER_EXECUTE), "/opt/init-sqs.sh")
            .apply {
                start()
                val result = execInContainer("bash", "/opt/init-sqs.sh")
                check(result.exitCode == 0) { "Falha ao criar as filas: ${result.stderr}" }
            }

    private companion object {
        const val OWNER_EXECUTE = 0b111_101_101
    }
}
