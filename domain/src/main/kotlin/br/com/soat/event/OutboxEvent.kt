package br.com.soat.event

import java.time.Instant
import java.util.UUID

data class OutboxEvent(
    val eventId: UUID,
    val eventType: String,
    val eventVersion: Int,
    val occurredAt: Instant,
    val payload: String,
    val traceparent: String? = null,
)
