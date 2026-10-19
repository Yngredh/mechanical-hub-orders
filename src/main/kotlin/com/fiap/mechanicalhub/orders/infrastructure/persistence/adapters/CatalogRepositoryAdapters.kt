package com.fiap.mechanicalhub.orders.infrastructure.persistence.adapters

import com.fiap.mechanicalhub.orders.application.ports.CustomerRepository
import com.fiap.mechanicalhub.orders.application.ports.RepairRepository
import com.fiap.mechanicalhub.orders.application.ports.VehicleRepository
import com.fiap.mechanicalhub.orders.domain.model.Customer
import com.fiap.mechanicalhub.orders.domain.model.Repair
import com.fiap.mechanicalhub.orders.domain.model.Vehicle
import com.fiap.mechanicalhub.orders.infrastructure.persistence.mappers.toDomain
import com.fiap.mechanicalhub.orders.infrastructure.persistence.repositories.CustomerJpaRepository
import com.fiap.mechanicalhub.orders.infrastructure.persistence.repositories.RepairJpaRepository
import com.fiap.mechanicalhub.orders.infrastructure.persistence.repositories.VehicleJpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class CustomerRepositoryAdapter(
    private val jpaRepository: CustomerJpaRepository,
) : CustomerRepository {
    override fun findById(id: UUID): Customer? = jpaRepository.findByIdAndDeletedAtIsNull(id)?.toDomain()
}

@Repository
class VehicleRepositoryAdapter(
    private val jpaRepository: VehicleJpaRepository,
) : VehicleRepository {
    override fun findById(id: UUID): Vehicle? = jpaRepository.findByIdAndDeletedAtIsNull(id)?.toDomain()
}

@Repository
class RepairRepositoryAdapter(
    private val jpaRepository: RepairJpaRepository,
) : RepairRepository {
    override fun findAllActive(): List<Repair> = jpaRepository.findAllByActiveTrueAndDeletedAtIsNullOrderByName().map { it.toDomain() }

    override fun findAllByIds(ids: Collection<UUID>): List<Repair> =
        if (ids.isEmpty()) emptyList() else jpaRepository.findAllByIdInAndDeletedAtIsNull(ids).map { it.toDomain() }
}
