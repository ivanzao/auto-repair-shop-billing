package br.com.soat.event

interface EventPublisher {
    fun publish(event: OutboxEvent)
}
