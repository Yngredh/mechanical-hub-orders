package com.fiap.mechanicalhub.orders.domain.exceptions

import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum
import java.util.UUID

/** Raiz das excecoes de dominio; a traducao para HTTP/mensagem fica na infrastructure. */
sealed class DomainException(
    message: String,
) : RuntimeException(message)

/** Transicao de status fora da tabela de order_status.md (HTTP 409, comando: erro permanente). */
class InvalidOrderTransitionException(
    orderId: UUID,
    currentStatus: OrderStatusEnum,
    action: String,
) : DomainException("Ordem $orderId em $currentStatus nao permite $action")

/** Regra do agregado violada com a ordem no status correto (HTTP 409). */
class OrderRuleViolationException(
    message: String,
) : DomainException(message)

/** Requisicao com referencias inconsistentes, como veiculo de outro cliente (HTTP 422). */
class InvalidOrderRequestException(
    message: String,
) : DomainException(message)

/** Recurso inexistente (HTTP 404). */
sealed class NotFoundException(
    message: String,
) : DomainException(message)

class ServiceOrderNotFoundException(
    reference: String,
) : NotFoundException("Ordem de servico $reference nao encontrada")

class CustomerNotFoundException(
    customerId: UUID,
) : NotFoundException("Cliente $customerId nao encontrado")

class VehicleNotFoundException(
    vehicleId: UUID,
) : NotFoundException("Veiculo $vehicleId nao encontrado")

class RepairNotFoundException(
    repairIds: Collection<UUID>,
) : NotFoundException("Reparo(s) inexistente(s) ou inativo(s): ${repairIds.joinToString()}")
