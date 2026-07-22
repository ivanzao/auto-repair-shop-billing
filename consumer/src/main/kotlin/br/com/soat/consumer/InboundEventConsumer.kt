package br.com.soat.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import br.com.soat.tracing.Tracing
import io.opentelemetry.api.trace.SpanKind
import org.slf4j.LoggerFactory

class InboundEventConsumer(
    private val queue: MessageQueue,
    handlers: List<InboundEventHandler>,
    private val mapper: ObjectMapper,
    private val coroutineDispatcher: CoroutineDispatcher,
) {
    private val logger = LoggerFactory.getLogger(InboundEventConsumer::class.java)

    private val byType: Map<String, List<InboundEventHandler>> =
        handlers.flatMap { handler -> handler.eventTypes.map { it to handler } }
            .groupBy({ it.first }, { it.second })

    fun start() {
        logger.info("Starting InboundEventConsumer")
        CoroutineScope(coroutineDispatcher).launch {
            while (isActive) poll()
        }
    }

    fun poll() {
        for (message in queue.receive(maxMessages = 10, waitSeconds = 20)) {
            val envelope = try {
                mapper.readValue(message.body, EventEnvelope::class.java)
            } catch (e: Exception) {
                logger.error("Failed to parse envelope, leaving message for redrive: {}", message.body, e)
                continue
            }

            if (dispatch(envelope, message.traceparent)) queue.delete(message.receiptHandle)
        }
    }

    private fun dispatch(envelope: EventEnvelope, traceparent: String?): Boolean {
        val span = Tracing.tracer
            .spanBuilder(envelope.eventType)
            .setSpanKind(SpanKind.CONSUMER)
            .setParent(Tracing.extractContext(traceparent))
            .startSpan()
        return try {
            span.makeCurrent().use { dispatchHandlers(envelope) }
        } finally {
            span.end()
        }
    }

    private fun dispatchHandlers(envelope: EventEnvelope): Boolean {
        val matched = byType[envelope.eventType].orEmpty()
        if (matched.isEmpty()) {
            logger.info(
                "No handler for eventType={} (eventId={}); dropping message",
                envelope.eventType, envelope.eventId,
            )
            return true
        }

        var allOk = true
        matched.forEach { handler ->
            try {
                handler.handle(envelope)
            } catch (e: Exception) {
                logger.error(
                    "Handler {} failed for event {}",
                    handler::class.simpleName, envelope.eventId, e,
                )
                allOk = false
            }
        }
        return allOk
    }
}
