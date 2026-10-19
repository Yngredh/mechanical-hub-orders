package com.fiap.mechanicalhub.orders.domain.enums

/**
 * Status da ordem exibido a mecanicos e clientes (business/order_status.md).
 * `RECEIVED` faz parte do contrato, mas nesta fase a ordem nasce em `IN_DIAGNOSIS`.
 */
enum class OrderStatusEnum(
    val displayName: String,
) {
    RECEIVED("Recebida"),
    IN_DIAGNOSIS("Em diagnóstico"),
    PREPARING("Em preparação"),
    WAITING_APPROVAL("Aguardando aprovação"),
    APPROVED("Aprovada"),
    REJECTED("Recusada"),
    AWAITING_PAYMENT("Aguardando pagamento"),
    READY_FOR_EXECUTION("Liberada para execução"),
    IN_PROGRESS("Em execução"),
    FINISHED("Finalizada"),
    DELIVERED("Entregue"),
    CANCELED("Cancelada"),
}
