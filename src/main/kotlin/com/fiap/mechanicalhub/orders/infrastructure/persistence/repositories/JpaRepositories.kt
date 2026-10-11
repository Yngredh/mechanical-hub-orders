package com.fiap.mechanicalhub.orders.infrastructure.persistence.repositories

import com.fiap.mechanicalhub.orders.infrastructure.persistence.entities.CustomerEntity
import com.fiap.mechanicalhub.orders.infrastructure.persistence.entities.OutboxEventEntity
import com.fiap.mechanicalhub.orders.infrastructure.persistence.entities.RepairEntity
import com.fiap.mechanicalhub.orders.infrastructure.persistence.entities.ServiceOrderEntity
import com.fiap.mechanicalhub.orders.infrastructure.persistence.entities.VehicleEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ServiceOrderJpaRepository :
    JpaRepository<ServiceOrderEntity, UUID>,
    JpaSpecificationExecutor<ServiceOrderEntity> {
    fun findByOrderNumber(orderNumber: String): ServiceOrderEntity?

    @Query(value = "SELECT nextval('service_order_number_seq')", nativeQuery = true)
    fun nextOrderSequence(): Long
}

interface CustomerJpaRepository : JpaRepository<CustomerEntity, UUID> {
    fun findByIdAndDeletedAtIsNull(id: UUID): CustomerEntity?
}

interface VehicleJpaRepository : JpaRepository<VehicleEntity, UUID> {
    fun findByIdAndDeletedAtIsNull(id: UUID): VehicleEntity?
}

interface RepairJpaRepository : JpaRepository<RepairEntity, UUID> {
    fun findAllByActiveTrueAndDeletedAtIsNullOrderByName(): List<RepairEntity>

    fun findAllByIdInAndDeletedAtIsNull(ids: Collection<UUID>): List<RepairEntity>
}

interface OutboxEventJpaRepository : JpaRepository<OutboxEventEntity, Long>
