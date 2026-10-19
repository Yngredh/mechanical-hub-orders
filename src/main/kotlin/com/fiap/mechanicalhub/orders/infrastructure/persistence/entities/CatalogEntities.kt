package com.fiap.mechanicalhub.orders.infrastructure.persistence.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Somente leitura: clientes vem das seeds (sem endpoint de cadastro nesta fase). */
@Entity
@Table(name = "customers")
class CustomerEntity(
    @Id
    var id: UUID,
    @Column(nullable = false)
    var name: String,
    @Column(name = "document_number", nullable = false)
    var documentNumber: String,
    @Column(nullable = false)
    var telephone: String,
    @Column(nullable = false)
    var email: String,
    @Column(name = "deleted_at")
    var deletedAt: Instant?,
)

@Entity
@Table(name = "vehicles")
class VehicleEntity(
    @Id
    var id: UUID,
    @Column(name = "customer_id", nullable = false)
    var customerId: UUID,
    @Column(name = "license_plate", nullable = false)
    var licensePlate: String,
    @Column(nullable = false)
    var brand: String,
    @Column(nullable = false)
    var model: String,
    @Column(name = "deleted_at")
    var deletedAt: Instant?,
)

/** Funcionario (`users`), lido para notificar o mecanico responsavel. */
@Entity
@Table(name = "users")
class EmployeeEntity(
    @Id
    var id: UUID,
    @Column(nullable = false)
    var name: String,
    @Column(nullable = false)
    var email: String,
    @Column(name = "deleted_at")
    var deletedAt: Instant?,
)

/** Catalogo de reparos (`services`); id = repairId. */
@Entity
@Table(name = "services")
class RepairEntity(
    @Id
    var id: UUID,
    @Column(nullable = false)
    var name: String,
    @Column
    var description: String?,
    @Column(nullable = false)
    var active: Boolean,
    @Column(name = "deleted_at")
    var deletedAt: Instant?,
)
