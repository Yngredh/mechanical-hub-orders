package com.fiap.mechanicalhub.orders.infrastructure.persistence.entities

import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum
import com.fiap.mechanicalhub.orders.domain.enums.TaskStatusEnum
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Modelo de persistencia de `service_orders`; o dominio nunca o enxerga. */
@Entity
@Table(name = "service_orders")
class ServiceOrderEntity(
    @Id
    var id: UUID,
    @Column(name = "order_number", nullable = false, updatable = false)
    var orderNumber: String,
    @Column(name = "customer_id", nullable = false, updatable = false)
    var customerId: UUID,
    @Column(name = "vehicle_id", nullable = false, updatable = false)
    var vehicleId: UUID,
    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    var createdByUserId: UUID,
    @Column(name = "responsible_user_id")
    var responsibleUserId: UUID?,
    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false)
    var status: OrderStatusEnum,
    @Column(name = "request_description", nullable = false)
    var requestDescription: String,
    @Column(name = "budget")
    var budget: BigDecimal?,
    @Column(name = "has_stock_pending", nullable = false)
    var hasStockPending: Boolean,
    @Column(name = "saga_transaction_id")
    var sagaTransactionId: UUID?,
    @Column(name = "approval_token_hash")
    var approvalTokenHash: String?,
    @Column(name = "opened_at", nullable = false, updatable = false)
    var openedAt: Instant,
    @Column(name = "completed_at")
    var completedAt: Instant?,
    @Column(name = "delivered_at")
    var deliveredAt: Instant?,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
    /** Controle otimista: aprovacao e recusa concorrentes nao podem ambas ter sucesso. */
    @Version
    @Column(name = "version", nullable = false)
    var version: Long,
) {
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "service_order_id", nullable = false, updatable = false)
    var tasks: MutableList<OrderTaskEntity> = mutableListOf()
}

@Entity
@Table(name = "order_tasks")
class OrderTaskEntity(
    @Id
    var id: UUID,
    @Column(name = "service_id", nullable = false, updatable = false)
    var repairId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "service_status", nullable = false)
    var status: TaskStatusEnum,
    @Column(name = "started_at")
    var startedAt: Instant?,
    @Column(name = "finished_at")
    var finishedAt: Instant?,
)
