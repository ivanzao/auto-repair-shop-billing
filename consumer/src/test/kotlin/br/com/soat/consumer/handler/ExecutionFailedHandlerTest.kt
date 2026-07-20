package br.com.soat.consumer.handler

import br.com.soat.payment.PaymentUseCase
import br.com.soat.consumer.EventEnvelope
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Test

class ExecutionFailedHandlerTest {

    private val mapper = ObjectMapper()
    private val useCase = mockk<PaymentUseCase>(relaxed = true)

    private fun envelope(payload: String) = EventEnvelope(
        eventId = UUID.randomUUID(),
        eventType = "ExecutionFailed",
        eventVersion = 1,
        occurredAt = Instant.parse("2026-07-18T14:03:00Z"),
        payload = mapper.readTree(payload),
    )

    @Test
    fun `translates the ExecutionFailed envelope into a refundPayment call`() {
        val orderId = UUID.randomUUID()
        val e = envelope("""{"orderId":"$orderId","paymentId":"PAY-1","reason":"boom"}""")

        ExecutionFailedHandler(useCase).handle(e)

        verify { useCase.refundPayment(orderId, "PAY-1") }
    }
}
