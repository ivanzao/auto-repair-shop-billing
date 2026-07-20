package br.com.soat.idempotency

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object Idempotencies : Table("idempotency") {
    val id = uuid("id")
    val entityId = uuid("entity_id")
    val idempotencyId = uuid("idempotency_id")
    val createdAt = datetime("created_at")

    init {
        PrimaryKey(id)
        uniqueIndex("uk_idempotency", entityId, idempotencyId)
    }
}
