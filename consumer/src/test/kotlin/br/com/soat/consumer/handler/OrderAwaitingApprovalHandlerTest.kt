package br.com.soat.consumer.handler

import br.com.soat.consumer.EventEnvelope
import br.com.soat.quote.QuoteListenerUseCase
import br.com.soat.quote.model.request.RegisterQuoteRequest
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OrderAwaitingApprovalHandlerTest {

    private val mapper = ObjectMapper()
    private val useCase = mockk<QuoteListenerUseCase>(relaxed = true)

    private val contractPayload = """
        {
          "orderId": "11111111-1111-1111-1111-111111111111",
          "reservationId": "44444444-4444-4444-4444-444444444444",
          "customer": {
            "name": "Maria Silva",
            "email": "maria@exemplo.com"
          },
          "services": [
            { "id": "55555555-5555-5555-5555-555555555555", "name": "Troca de oleo", "price": 100.00 }
          ],
          "supplies": [
            { "id": "66666666-6666-6666-6666-666666666666", "name": "Filtro de oleo", "quantity": 2, "unitPrice": 30.00 }
          ],
          "totalAmount": 160.00
        }
    """.trimIndent()

    private val envelope = EventEnvelope(
        eventId = UUID.randomUUID(),
        eventType = "OrderAwaitingApproval",
        eventVersion = 1,
        occurredAt = Instant.parse("2026-07-25T18:00:00Z"),
        payload = mapper.readTree(contractPayload),
    )

    @Test
    fun `listens to OrderAwaitingApproval only`() {
        assertEquals(setOf("OrderAwaitingApproval"), OrderAwaitingApprovalHandler(useCase).eventTypes)
    }

    @Test
    fun `translates the OrderAwaitingApproval envelope into a RegisterQuoteRequest`() {
        OrderAwaitingApprovalHandler(useCase).handle(envelope)

        val captured = slot<RegisterQuoteRequest>()
        verify { useCase.createQuote(capture(captured), envelope.eventId) }
        val request = captured.captured
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), request.orderId)
        assertEquals("Maria Silva", request.customerName)
        assertEquals("maria@exemplo.com", request.customerEmail)
        assertEquals(1, request.services.size)
        assertEquals("Troca de oleo", request.services[0].name)
        assertEquals(0, BigDecimal("100.00").compareTo(request.services[0].price))
        assertEquals(1, request.supplies.size)
        assertEquals("Filtro de oleo", request.supplies[0].name)
        assertEquals(2, request.supplies[0].quantity)
        assertEquals(0, BigDecimal("30.00").compareTo(request.supplies[0].unitPrice))
        assertEquals(0, BigDecimal("160.00").compareTo(request.totalAmount))
    }

    @Test
    fun `carries the reservationId the compensations depend on`() {
        OrderAwaitingApprovalHandler(useCase).handle(envelope)

        val captured = slot<RegisterQuoteRequest>()
        verify { useCase.createQuote(capture(captured), envelope.eventId) }
        assertEquals(
            UUID.fromString("44444444-4444-4444-4444-444444444444"),
            captured.captured.reservationId,
        )
    }
}
