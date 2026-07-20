package br.com.soat.idempotency

import br.com.soat.shared.repository.IdempotencyRepository
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class IdempotencyPostgresRepository : IdempotencyRepository {

    override fun exists(entityId: UUID, idempotencyId: UUID): Boolean = transaction {
        Idempotencies.selectAll()
            .where {
                (Idempotencies.entityId eq entityId) and (Idempotencies.idempotencyId eq idempotencyId)
            }
            .count() > 0
    }

    override fun save(entityId: UUID, idempotencyId: UUID) {
        transaction {
            Idempotencies.insert {
                it[id] = UUID.randomUUID()
                it[Idempotencies.entityId] = entityId
                it[Idempotencies.idempotencyId] = idempotencyId
                it[createdAt] = LocalDateTime.now().toKotlinLocalDateTime()
            }
        }
    }
}
