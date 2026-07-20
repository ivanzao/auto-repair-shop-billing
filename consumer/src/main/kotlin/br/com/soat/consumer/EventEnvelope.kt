package br.com.soat.consumer

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

data class EventEnvelope(
    val eventId: UUID,
    val eventType: String,
    val eventVersion: Int,
    val occurredAt: Instant,
    val payload: JsonNode,
)
