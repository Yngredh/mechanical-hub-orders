package com.fiap.mechanicalhub.orders.infrastructure.persistence.adapters

import com.fiap.mechanicalhub.orders.application.ports.ServiceOrderFilter
import com.fiap.mechanicalhub.orders.application.ports.ServiceOrderRepository
import com.fiap.mechanicalhub.orders.domain.model.ServiceOrder
import com.fiap.mechanicalhub.orders.infrastructure.persistence.entities.ServiceOrderEntity
import com.fiap.mechanicalhub.orders.infrastructure.persistence.mappers.toDomain
import com.fiap.mechanicalhub.orders.infrastructure.persistence.mappers.toNewEntity
import com.fiap.mechanicalhub.orders.infrastructure.persistence.mappers.updateFrom
import com.fiap.mechanicalhub.orders.infrastructure.persistence.repositories.ServiceOrderJpaRepository
import jakarta.persistence.EntityManager
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Repository
import java.time.Clock
import java.util.UUID

@Repository
class ServiceOrderRepositoryAdapter(
    private val jpaRepository: ServiceOrderJpaRepository,
    private val entityManager: EntityManager,
    private val clock: Clock,
) : ServiceOrderRepository {
    override fun findById(id: UUID): ServiceOrder? = jpaRepository.findById(id).map { it.toDomain() }.orElse(null)

    override fun findByOrderNumber(orderNumber: String): ServiceOrder? = jpaRepository.findByOrderNumber(orderNumber)?.toDomain()

    override fun search(filter: ServiceOrderFilter): List<ServiceOrder> {
        val specification =
            listOfNotNull(
                filter.status?.let { status ->
                    Specification<ServiceOrderEntity> { root, _, cb -> cb.equal(root.get<Any>("status"), status) }
                },
                filter.customerId?.let { id ->
                    Specification<ServiceOrderEntity> { root, _, cb -> cb.equal(root.get<UUID>("customerId"), id) }
                },
                filter.responsibleUserId?.let { id ->
                    Specification<ServiceOrderEntity> { root, _, cb -> cb.equal(root.get<UUID>("responsibleUserId"), id) }
                },
            ).fold(Specification.unrestricted<ServiceOrderEntity>()) { acc, spec -> acc.and(spec) }
        return jpaRepository.findAll(specification, Sort.by(Sort.Direction.DESC, "openedAt")).map { it.toDomain() }
    }

    override fun save(order: ServiceOrder): ServiceOrder {
        val now = clock.instant()
        val existing = jpaRepository.findById(order.id).orElse(null)
        val entity =
            if (existing == null) {
                order.toNewEntity(now).also(entityManager::persist)
            } else {
                if (existing.version != order.version) {
                    throw ObjectOptimisticLockingFailureException(ServiceOrderEntity::class.java, order.id)
                }
                existing.apply { updateFrom(order, now) }
            }
        // Flush aqui para o conflito de versao aparecer dentro do caso de uso.
        entityManager.flush()
        return entity.toDomain()
    }

    override fun nextOrderSequence(): Long = jpaRepository.nextOrderSequence()
}
