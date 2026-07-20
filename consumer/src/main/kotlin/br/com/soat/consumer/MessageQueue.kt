package br.com.soat.consumer

data class Message(val body: String, val receiptHandle: String)

interface MessageQueue {
    fun receive(maxMessages: Int, waitSeconds: Int): List<Message>
    fun delete(receiptHandle: String)
}
