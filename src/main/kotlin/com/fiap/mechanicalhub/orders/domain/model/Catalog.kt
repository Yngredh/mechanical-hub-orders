package com.fiap.mechanicalhub.orders.domain.model

import java.util.UUID

/** Reparo do catalogo (somente leitura, sem preco). */
data class Repair(
    val id: UUID,
    val name: String,
    val description: String?,
    val active: Boolean,
)

data class Customer(
    val id: UUID,
    val name: String,
    val documentNumber: String,
    val telephone: String,
    val email: String,
)

data class Vehicle(
    val id: UUID,
    val customerId: UUID,
    val licensePlate: String,
    val brand: String,
    val model: String,
)
