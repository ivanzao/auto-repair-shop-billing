package br.com.soat.event.repository

import br.com.soat.event.OutboxEvent
import br.com.soat.event.model.DomainEvent
import java.time.Duration
import java.util.UUID

interface OutboxRepository {
    fun save(event: DomainEvent): OutboxEvent

    fun findPendingOlderThan(age: Duration, limit: Int): List<OutboxEvent>

    fun delete(eventId: UUID)
}
