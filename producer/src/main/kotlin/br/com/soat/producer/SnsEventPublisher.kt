package br.com.soat.producer

import br.com.soat.event.EventPublisher
import br.com.soat.event.OutboxEvent
import br.com.soat.event.repository.OutboxRepository
import org.slf4j.LoggerFactory

class SnsEventPublisher(
    private val outbox: OutboxRepository,
    private val sns: SnsPort,
    private val serializer: EventEnvelopeSerializer,
) : EventPublisher {

    private val logger = LoggerFactory.getLogger(SnsEventPublisher::class.java)

    override fun publish(event: OutboxEvent) {
        try {
            sns.publish(
                payload = serializer.toJson(event),
                eventType = event.eventType,
                messageId = event.eventId.toString(),
                traceparent = event.traceparent,
            )
            outbox.delete(event.eventId)
            logger.info("Published event {} ({}) to SNS", event.eventId, event.eventType)
        } catch (e: Exception) {
            logger.warn(
                "Failed to publish event {} ({}); leaving it in the outbox for retry",
                event.eventId, event.eventType, e,
            )
        }
    }
}
