package br.com.soat.scheduler.task

import br.com.soat.producer.OutboxRelay
import br.com.soat.scheduler.ScheduledTask
import org.slf4j.LoggerFactory

class OutboxRelayTask(
    private val outboxRelay: OutboxRelay
) : ScheduledTask {

    private val logger = LoggerFactory.getLogger(OutboxRelayTask::class.java)

    override fun execute() {
        logger.debug("Relaying pending outbox events")
        outboxRelay.relayPending()
    }

    override fun getLockName(): String = "relay-outbox-events"

    override fun getIntervalInSeconds(): Long = 5
}
