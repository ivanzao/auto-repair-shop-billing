package br.com.soat.consumer

interface InboundEventHandler {
    val eventTypes: Set<String>
    fun handle(envelope: EventEnvelope)
}
