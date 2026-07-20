package br.com.soat.event.model

import java.time.Instant
import java.util.UUID

abstract class DomainEvent(
    val eventId: UUID = UUID.randomUUID(),
    val eventVersion: Int = 1,
    val occurredAt: Instant = Instant.now(),
) {
    abstract val eventType: String
}
