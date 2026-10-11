package com.fiap.mechanicalhub.orders.domain.enums

/** Status de um reparo da ordem: `PENDING → STARTED → FINISHED`. */
enum class TaskStatusEnum(
    val displayName: String,
) {
    PENDING("Pendente"),
    STARTED("Iniciado"),
    FINISHED("Finalizado"),
    ;

    fun canMoveTo(target: TaskStatusEnum): Boolean =
        when (this) {
            PENDING -> target == STARTED
            STARTED -> target == FINISHED
            FINISHED -> false
        }
}
