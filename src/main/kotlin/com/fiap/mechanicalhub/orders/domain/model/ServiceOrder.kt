package com.fiap.mechanicalhub.orders.domain.model

import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.APPROVED
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.AWAITING_PAYMENT
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.CANCELED
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.DELIVERED
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.FINISHED
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.IN_DIAGNOSIS
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.IN_PROGRESS
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.PREPARING
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.READY_FOR_EXECUTION
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.REJECTED
import com.fiap.mechanicalhub.orders.domain.enums.OrderStatusEnum.WAITING_APPROVAL
import com.fiap.mechanicalhub.orders.domain.enums.TaskStatusEnum
import com.fiap.mechanicalhub.orders.domain.exceptions.InvalidOrderRequestException
import com.fiap.mechanicalhub.orders.domain.exceptions.InvalidOrderTransitionException
import com.fiap.mechanicalhub.orders.domain.exceptions.OrderRuleViolationException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Agregado da ordem de servico. Cada metodo de transicao valida o status de
 * origem de business/order_status.md e devolve uma nova instancia; nada e
 * alterado quando a transicao e invalida.
 */
data class ServiceOrder(
    val id: UUID,
    val orderNumber: String,
    val customerId: UUID,
    val vehicleId: UUID,
    val createdByUserId: UUID,
    val responsibleUserId: UUID?,
    val status: OrderStatusEnum,
    val requestDescription: String,
    val tasks: List<OrderTask>,
    val openedAt: Instant,
    val budget: BigDecimal? = null,
    val hasStockPending: Boolean = false,
    val sagaTransactionId: UUID? = null,
    val approvalTokenHash: String? = null,
    val completedAt: Instant? = null,
    val deliveredAt: Instant? = null,
    val version: Long = 0,
) {
    val repairIds: List<UUID> get() = tasks.map { it.repairId }

    /** Reparos editaveis somente durante o diagnostico; reparo ja presente e ignorado. */
    fun addRepairs(
        repairIds: Collection<UUID>,
        newTaskId: () -> UUID,
    ): ServiceOrder {
        requireStatus(IN_DIAGNOSIS, "adicionar reparos")
        val newTasks =
            repairIds
                .distinct()
                .filterNot { it in this.repairIds }
                .map { OrderTask(id = newTaskId(), repairId = it) }
        return copy(tasks = tasks + newTasks)
    }

    fun finishDiagnosis(): ServiceOrder {
        requireStatus(IN_DIAGNOSIS, "finalizar o diagnostico")
        if (tasks.isEmpty()) {
            throw OrderRuleViolationException("Ordem $id precisa de ao menos um reparo para finalizar o diagnostico")
        }
        return copy(status = PREPARING)
    }

    fun requestApproval(
        transactionId: UUID,
        budget: BigDecimal,
        approvalTokenHash: String,
    ): ServiceOrder {
        requireStatus(PREPARING, "solicitar aprovacao")
        requireNoStockPending("enviada para aprovacao")
        return copy(
            status = WAITING_APPROVAL,
            budget = budget,
            sagaTransactionId = transactionId,
            approvalTokenHash = approvalTokenHash,
        )
    }

    fun approve(): ServiceOrder = moveFrom(WAITING_APPROVAL, to = APPROVED, action = "aprovar")

    fun reject(): ServiceOrder = moveFrom(WAITING_APPROVAL, to = REJECTED, action = "recusar")

    fun awaitPayment(): ServiceOrder = moveFrom(APPROVED, to = AWAITING_PAYMENT, action = "aguardar pagamento")

    fun releaseForExecution(): ServiceOrder = moveFrom(AWAITING_PAYMENT, to = READY_FOR_EXECUTION, action = "liberar para execucao")

    fun cancel(): ServiceOrder = moveFrom(AWAITING_PAYMENT, to = CANCELED, action = "cancelar")

    fun startExecution(): ServiceOrder {
        requireStatus(READY_FOR_EXECUTION, "iniciar a execucao")
        requireNoStockPending("executada")
        return copy(status = IN_PROGRESS)
    }

    fun finish(now: Instant): ServiceOrder {
        requireStatus(IN_PROGRESS, "finalizar")
        val unfinished = tasks.filter { it.status != TaskStatusEnum.FINISHED }
        if (unfinished.isNotEmpty()) {
            throw OrderRuleViolationException("Ordem $id possui ${unfinished.size} reparo(s) nao finalizado(s)")
        }
        return copy(status = FINISHED, completedAt = now)
    }

    fun deliver(now: Instant): ServiceOrder {
        requireStatus(FINISHED, "entregar")
        return copy(status = DELIVERED, deliveredAt = now)
    }

    fun markStockPending(transactionId: UUID): ServiceOrder {
        requireStatus(PREPARING, "marcar pendencia de estoque")
        return copy(hasStockPending = true, sagaTransactionId = transactionId)
    }

    fun clearStockPending(): ServiceOrder = copy(hasStockPending = false)

    /** Status do reparo durante a execucao; nao movimenta estoque. */
    fun updateTaskStatus(
        repairId: UUID,
        target: TaskStatusEnum,
        now: Instant,
    ): ServiceOrder {
        requireStatus(IN_PROGRESS, "atualizar reparos")
        val task =
            tasks.firstOrNull { it.repairId == repairId }
                ?: throw InvalidOrderRequestException("Reparo $repairId nao pertence a ordem $id")
        if (!task.status.canMoveTo(target)) {
            throw OrderRuleViolationException("Reparo $repairId em ${task.status} nao pode ir para $target")
        }
        return copy(tasks = tasks.map { if (it.repairId == repairId) it.moveTo(target, now) else it })
    }

    private fun moveFrom(
        expected: OrderStatusEnum,
        to: OrderStatusEnum,
        action: String,
    ): ServiceOrder {
        requireStatus(expected, action)
        return copy(status = to)
    }

    private fun requireStatus(
        expected: OrderStatusEnum,
        action: String,
    ) {
        if (status != expected) throw InvalidOrderTransitionException(id, status, action)
    }

    private fun requireNoStockPending(action: String) {
        if (hasStockPending) {
            throw OrderRuleViolationException("Ordem $id com pendencia de estoque nao pode ser $action")
        }
    }

    companion object {
        /** Abre a ordem ja em `IN_DIAGNOSIS`, com os reparos em `PENDING`. */
        fun open(
            data: NewServiceOrder,
            newTaskId: () -> UUID,
        ): ServiceOrder {
            if (data.repairIds.isEmpty()) {
                throw InvalidOrderRequestException("A ordem precisa de ao menos um reparo")
            }
            return ServiceOrder(
                id = data.id,
                orderNumber = data.orderNumber,
                customerId = data.customerId,
                vehicleId = data.vehicleId,
                createdByUserId = data.createdByUserId,
                responsibleUserId = data.createdByUserId,
                status = IN_DIAGNOSIS,
                requestDescription = data.requestDescription,
                tasks = data.repairIds.distinct().map { OrderTask(id = newTaskId(), repairId = it) },
                openedAt = data.openedAt,
            )
        }
    }
}

/** Dados de abertura da ordem. */
data class NewServiceOrder(
    val id: UUID,
    val orderNumber: String,
    val customerId: UUID,
    val vehicleId: UUID,
    val createdByUserId: UUID,
    val requestDescription: String,
    val repairIds: List<UUID>,
    val openedAt: Instant,
)
