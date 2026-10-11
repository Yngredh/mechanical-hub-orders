package com.fiap.mechanicalhub.orders

import org.springframework.boot.fromApplication
import org.springframework.boot.with

fun main(args: Array<String>) {
    fromApplication<MechanicalHubOrdersApplication>().with(TestcontainersConfiguration::class).run(*args)
}
