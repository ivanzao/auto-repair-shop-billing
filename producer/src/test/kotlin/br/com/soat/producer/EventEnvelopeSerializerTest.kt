package br.com.soat.producer

import br.com.soat.event.OutboxEvent
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventEnvelopeSerializerTest {

    private val serializer = EventEnvelopeSerializer(ObjectMapper())
    private val mapper = ObjectMapper()

    @Test
    fun `wraps the payload in the contract envelope`() {
        val id = UUID.randomUUID()
        val event = OutboxEvent(
            eventId = id,
            eventType = "PaymentConfirmed",
            eventVersion = 1,
            occurredAt = Instant.parse("2026-07-18T14:03:00Z"),
            payload = """{"orderId":"$id","services":[]}""",
        )

        val envelope = mapper.readTree(serializer.toJson(event))

        assertEquals(id.toString(), envelope["eventId"].asText())
        assertEquals("PaymentConfirmed", envelope["eventType"].asText())
        assertEquals(1, envelope["eventVersion"].asInt())
        assertEquals("2026-07-18T14:03:00Z", envelope["occurredAt"].asText())
        assertEquals(id.toString(), envelope["payload"]["orderId"].asText())
        assertTrue(envelope["payload"]["services"].isArray)
    }
}
