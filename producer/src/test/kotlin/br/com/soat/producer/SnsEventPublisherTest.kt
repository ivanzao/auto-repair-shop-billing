package br.com.soat.producer

import br.com.soat.event.OutboxEvent
import br.com.soat.event.repository.OutboxRepository
import br.com.soat.event.model.DomainEvent
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SnsEventPublisherTest {

    private class FakeOutbox : OutboxRepository {
        val deleted = mutableListOf<UUID>()
        override fun save(event: DomainEvent): OutboxEvent = throw UnsupportedOperationException()
        override fun findPendingOlderThan(age: Duration, limit: Int): List<OutboxEvent> = emptyList()
        override fun delete(eventId: UUID) { deleted += eventId }
    }

    private fun event() = OutboxEvent(
        eventId = UUID.randomUUID(),
        eventType = "PaymentConfirmed",
        eventVersion = 1,
        occurredAt = Instant.parse("2026-07-18T14:03:00Z"),
        payload = """{"orderId":"${UUID.randomUUID()}"}""",
    )

    private fun publisher(outbox: OutboxRepository, sns: SnsPort) =
        SnsEventPublisher(outbox, sns, EventEnvelopeSerializer(ObjectMapper()))

    @Test
    fun `deletes the outbox row after a successful publish`() {
        val outbox = FakeOutbox()
        val published = mutableListOf<String>()
        val sns = object : SnsPort {
            override fun publish(payload: String, eventType: String, messageId: String, traceparent: String?) {
                published += eventType
            }
        }
        val e = event()

        publisher(outbox, sns).publish(e)

        assertEquals(listOf("PaymentConfirmed"), published)
        assertEquals(listOf(e.eventId), outbox.deleted)
    }

    @Test
    fun `keeps the outbox row and does not throw when publishing fails`() {
        val outbox = FakeOutbox()
        val sns = object : SnsPort {
            override fun publish(payload: String, eventType: String, messageId: String, traceparent: String?) {
                throw RuntimeException("sns down")
            }
        }

        publisher(outbox, sns).publish(event())

        assertTrue(outbox.deleted.isEmpty(), "falha no publish tem que preservar a linha para o relay")
    }
}
