package com.fiap.mechanicalhub.orders.application.ports

import com.fiap.mechanicalhub.orders.domain.model.Customer
import com.fiap.mechanicalhub.orders.domain.model.Repair
import com.fiap.mechanicalhub.orders.domain.model.Vehicle
import java.util.UUID

interface CustomerRepository {
    fun findById(id: UUID): Customer?
}

interface VehicleRepository {
    fun findById(id: UUID): Vehicle?
}

interface RepairRepository {
    fun findAllActive(): List<Repair>

    fun findAllByIds(ids: Collection<UUID>): List<Repair>
}
