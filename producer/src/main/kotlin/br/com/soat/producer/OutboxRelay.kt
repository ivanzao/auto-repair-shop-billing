package br.com.soat.producer

import br.com.soat.event.EventPublisher
import br.com.soat.event.repository.OutboxRepository
import java.time.Duration
import org.slf4j.LoggerFactory

class OutboxRelay(
    private val outbox: OutboxRepository,
    private val publisher: EventPublisher,
) {
    private val logger = LoggerFactory.getLogger(OutboxRelay::class.java)

    fun relayPending(age: Duration = Duration.ofMinutes(1), batch: Int = 10) {
        val pending = outbox.findPendingOlderThan(age, batch)
        if (pending.isEmpty()) return

        logger.info("Retrying {} outbox event(s) older than {}", pending.size, age)
        pending.forEach { publisher.publish(it) }
    }
}
