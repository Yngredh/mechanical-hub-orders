package com.fiap.mechanicalhub.orders.infrastructure.http.controllers

import com.fiap.mechanicalhub.orders.application.usecases.customer.ApproveOrderUseCase
import com.fiap.mechanicalhub.orders.application.usecases.customer.GetCustomerOrderUseCase
import com.fiap.mechanicalhub.orders.application.usecases.customer.RejectOrderUseCase
import com.fiap.mechanicalhub.orders.infrastructure.http.dto.CustomerOrderResponse
import com.fiap.mechanicalhub.orders.infrastructure.http.dto.toResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Endpoints publicos do cliente, autenticados pelo token de aprovacao. */
@RestController
@RequestMapping("/mechanical-hub/service-orders")
@Tag(name = "Cliente", description = "Endpoints publicos (header X-Approval-Token)")
class ExternalController(
    private val getCustomerOrder: GetCustomerOrderUseCase,
    private val approveOrder: ApproveOrderUseCase,
    private val rejectOrder: RejectOrderUseCase,
) {
    @GetMapping("/{orderNumber}")
    @Operation(summary = "Resumo, status e orcamento da ordem")
    @ApiResponse(responseCode = "404", description = "Ordem inexistente ou token ausente/invalido")
    fun find(
        @PathVariable orderNumber: String,
        @RequestHeader(APPROVAL_TOKEN_HEADER, required = false) approvalToken: String?,
    ): CustomerOrderResponse = getCustomerOrder.execute(orderNumber, approvalToken).toResponse()

    @PostMapping("/{orderId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Aprova o orcamento (somente em WAITING_APPROVAL)")
    @ApiResponse(responseCode = "409", description = "Ordem fora de WAITING_APPROVAL ou decisao concorrente")
    fun approve(
        @PathVariable orderId: UUID,
        @RequestHeader(APPROVAL_TOKEN_HEADER, required = false) approvalToken: String?,
    ) = approveOrder.execute(orderId, approvalToken)

    @PostMapping("/{orderId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Recusa o orcamento (somente em WAITING_APPROVAL)")
    @ApiResponse(responseCode = "409", description = "Ordem fora de WAITING_APPROVAL ou decisao concorrente")
    fun reject(
        @PathVariable orderId: UUID,
        @RequestHeader(APPROVAL_TOKEN_HEADER, required = false) approvalToken: String?,
    ) = rejectOrder.execute(orderId, approvalToken)

    private companion object {
        const val APPROVAL_TOKEN_HEADER = "X-Approval-Token"
    }
}
