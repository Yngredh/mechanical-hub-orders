package com.fiap.mechanicalhub.orders.infrastructure.http.controllers

import com.fiap.mechanicalhub.orders.application.usecases.catalog.ListRepairsUseCase
import com.fiap.mechanicalhub.orders.infrastructure.http.dto.RepairResponse
import com.fiap.mechanicalhub.orders.infrastructure.http.dto.toResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Catalogo", description = "Catalogo de reparos (somente leitura, sem preco)")
class CatalogController(
    private val listRepairs: ListRepairsUseCase,
) {
    @GetMapping("/services")
    @Operation(summary = "Lista os reparos ativos")
    fun list(): List<RepairResponse> = listRepairs.execute().map { it.toResponse() }
}
