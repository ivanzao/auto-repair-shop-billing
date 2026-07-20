package br.com.soat.shared.repository

import java.util.UUID

interface IdempotencyRepository {
    fun exists(entityId: UUID, idempotencyId: UUID): Boolean
    fun save(entityId: UUID, idempotencyId: UUID)
}
