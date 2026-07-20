package br.com.soat.producer

import br.com.soat.event.OutboxEvent
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

class EventEnvelopeSerializer(private val mapper: ObjectMapper) {

    fun toJson(event: OutboxEvent): String {
        val node = mapper.createObjectNode().apply {
            put("eventId", event.eventId.toString())
            put("eventType", event.eventType)
            put("eventVersion", event.eventVersion)
            put("occurredAt", event.occurredAt.toString())
            set<JsonNode>("payload", mapper.readTree(event.payload))
        }
        return mapper.writeValueAsString(node)
    }
}
