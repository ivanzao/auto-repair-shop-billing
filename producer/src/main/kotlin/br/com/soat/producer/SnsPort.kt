package br.com.soat.producer

interface SnsPort {
    fun publish(payload: String, eventType: String, messageId: String, traceparent: String? = null)
}
