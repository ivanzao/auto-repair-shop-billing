package br.com.soat.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InboundEventConsumerTest {

    private val mapper = ObjectMapper().findAndRegisterModules()

    private class FakeQueue(messages: List<Message>) : MessageQueue {
        private val pending = ArrayDeque(messages)
        val deleted = mutableListOf<String>()
        override fun receive(maxMessages: Int, waitSeconds: Int): List<Message> {
            val batch = mutableListOf<Message>()
            while (pending.isNotEmpty() && batch.size < maxMessages) batch += pending.removeFirst()
            return batch
        }
        override fun delete(receiptHandle: String) { deleted += receiptHandle }
    }

    private fun envelopeJson(type: String, orderId: UUID = UUID.randomUUID()) = """
        {"eventId":"${UUID.randomUUID()}","eventType":"$type","eventVersion":1,
         "occurredAt":"2026-07-18T14:03:00Z","payload":{"orderId":"$orderId"}}
    """.trimIndent()

    private fun consumer(queue: MessageQueue, handlers: List<InboundEventHandler>) =
        InboundEventConsumer(queue, handlers, mapper, Dispatchers.Unconfined)

    @Test
    fun `fans out to the matching handler and deletes the message`() {
        val handled = mutableListOf<UUID>()
        val handler = object : InboundEventHandler {
            override val eventTypes = setOf(EventType.ORDER_AWAITING_APPROVAL)
            override fun handle(envelope: EventEnvelope) { handled += envelope.eventId }
        }
        val queue = FakeQueue(listOf(Message(envelopeJson(EventType.ORDER_AWAITING_APPROVAL), "rh-1")))

        consumer(queue, listOf(handler)).poll()

        assertEquals(1, handled.size)
        assertEquals(listOf("rh-1"), queue.deleted)
    }

    @Test
    fun `fans out to every handler registered for the same eventType`() {
        val calls = mutableListOf<String>()
        val first = object : InboundEventHandler {
            override val eventTypes = setOf(EventType.EXECUTION_FAILED)
            override fun handle(envelope: EventEnvelope) { calls += "first" }
        }
        val second = object : InboundEventHandler {
            override val eventTypes = setOf(EventType.EXECUTION_FAILED)
            override fun handle(envelope: EventEnvelope) { calls += "second" }
        }
        val queue = FakeQueue(listOf(Message(envelopeJson(EventType.EXECUTION_FAILED), "rh-2")))

        consumer(queue, listOf(first, second)).poll()

        assertEquals(listOf("first", "second"), calls)
        assertEquals(listOf("rh-2"), queue.deleted)
    }

    @Test
    fun `deletes an unknown eventType without a handler (mesh superset)`() {
        val queue = FakeQueue(listOf(Message(envelopeJson("SomethingWeIgnore"), "rh-3")))

        consumer(queue, emptyList()).poll()

        assertEquals(listOf("rh-3"), queue.deleted)
    }

    @Test
    fun `does not delete a message whose handler failed`() {
        val handler = object : InboundEventHandler {
            override val eventTypes = setOf(EventType.ORDER_AWAITING_APPROVAL)
            override fun handle(envelope: EventEnvelope): Unit = throw RuntimeException("boom")
        }
        val queue = FakeQueue(listOf(Message(envelopeJson(EventType.ORDER_AWAITING_APPROVAL), "rh-4")))

        consumer(queue, listOf(handler)).poll()

        assertTrue(queue.deleted.isEmpty())
    }

    @Test
    fun `skips an unparseable message without crashing`() {
        val queue = FakeQueue(listOf(Message("not json", "rh-5")))

        consumer(queue, emptyList()).poll()

        assertTrue(queue.deleted.isEmpty())
    }
}
