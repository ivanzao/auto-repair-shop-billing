package br.com.soat.payment

import br.com.soat.quote.persistQuote
import br.com.soat.IntegrationTest
import br.com.soat.quote.QuoteStatus
import br.com.soat.quote.repository.QuoteRepository
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExecutionFailedIntegrationTest : IntegrationTest() {

    private val quoteRepository: QuoteRepository by lazy { get<QuoteRepository>() }

    private fun executionFailedEnvelope(orderId: UUID, paymentId: String, eventId: UUID = UUID.randomUUID()): String =
        """
        {
          "eventId":"$eventId",
          "eventType":"ExecutionFailed",
          "eventVersion":1,
          "occurredAt":"${Instant.now()}",
          "payload":{"orderId":"$orderId","paymentId":"$paymentId","reason":"execution_failed"}
        }
        """.trimIndent()

    @Test
    fun `ExecutionFailed refunds the payment and marks the quote REFUNDED`() {
        val quote = persistQuote(status = QuoteStatus.PAID, paymentId = "PAY-1")

        sendSagaMessage(executionFailedEnvelope(quote.orderId, "PAY-1"), "ExecutionFailed")

        await { quoteRepository.findByOrderId(quote.orderId)?.status == QuoteStatus.REFUNDED }
        assertEquals(QuoteStatus.REFUNDED, quoteRepository.findByOrderId(quote.orderId)!!.status)
    }

    @Test
    fun `a redelivered ExecutionFailed keeps the quote REFUNDED`() {
        val quote = persistQuote(status = QuoteStatus.PAID, paymentId = "PAY-1")
        val envelope = executionFailedEnvelope(quote.orderId, "PAY-1")

        sendSagaMessage(envelope, "ExecutionFailed")
        sendSagaMessage(envelope, "ExecutionFailed")

        await { quoteRepository.findByOrderId(quote.orderId)?.status == QuoteStatus.REFUNDED }
        await { sagaQueueDepth() == 0 }
        Thread.sleep(1000)
        assertEquals(QuoteStatus.REFUNDED, quoteRepository.findByOrderId(quote.orderId)!!.status)
    }
}
