package br.com.soat.consumer

data class Message(val body: String, val receiptHandle: String, val traceparent: String? = null)

interface MessageQueue {
    fun receive(maxMessages: Int, waitSeconds: Int): List<Message>
    fun delete(receiptHandle: String)
}
