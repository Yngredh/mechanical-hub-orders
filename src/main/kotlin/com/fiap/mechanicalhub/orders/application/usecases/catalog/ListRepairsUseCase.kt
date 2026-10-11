package com.fiap.mechanicalhub.orders.application.usecases.catalog

import com.fiap.mechanicalhub.orders.application.ports.RepairRepository
import com.fiap.mechanicalhub.orders.domain.model.Repair
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** GET /services: catalogo de reparos (somente leitura, sem preco). */
@Service
class ListRepairsUseCase(
    private val repairRepository: RepairRepository,
) {
    @Transactional(readOnly = true)
    fun execute(): List<Repair> = repairRepository.findAllActive()
}
