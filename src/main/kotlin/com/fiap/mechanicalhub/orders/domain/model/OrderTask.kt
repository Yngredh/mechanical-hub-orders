package com.fiap.mechanicalhub.orders.domain.model

import com.fiap.mechanicalhub.orders.domain.enums.TaskStatusEnum
import java.time.Instant
import java.util.UUID

/** Reparo da ordem (`order_tasks`); `repairId` e o id do catalogo (`services`). */
data class OrderTask(
    val id: UUID,
    val repairId: UUID,
    val status: TaskStatusEnum = TaskStatusEnum.PENDING,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
) {
    fun moveTo(
        target: TaskStatusEnum,
        now: Instant,
    ): OrderTask =
        when (target) {
            TaskStatusEnum.PENDING -> copy(status = target)
            TaskStatusEnum.STARTED -> copy(status = target, startedAt = now)
            TaskStatusEnum.FINISHED -> copy(status = target, finishedAt = now)
        }
}
