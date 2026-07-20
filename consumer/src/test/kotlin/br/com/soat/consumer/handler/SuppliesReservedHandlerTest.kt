package br.com.soat.consumer.handler

import br.com.soat.quote.QuoteListenerUseCase
import br.com.soat.quote.model.request.RegisterQuoteRequest
import br.com.soat.consumer.EventEnvelope
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SuppliesReservedHandlerTest {

    private val mapper = com.fasterxml.jackson.databind.ObjectMapper()
    private val useCase = mockk<QuoteListenerUseCase>(relaxed = true)

    private fun envelope(payload: String) = EventEnvelope(
        eventId = UUID.randomUUID(),
        eventType = "SuppliesReserved",
        eventVersion = 1,
        occurredAt = Instant.parse("2026-07-18T14:03:00Z"),
        payload = mapper.readTree(payload),
    )

    @Test
    fun `translates the SuppliesReserved envelope into a RegisterQuoteRequest`() {
        val orderId = UUID.randomUUID()
        val reservationId = UUID.randomUUID()
        val e = envelope(
            """
            {
              "orderId":"$orderId",
              "reservationId":"$reservationId",
              "customer":{"name":"John","email":"john@example.com"},
              "services":[{"name":"Oil Change","price":100.00}],
              "supplies":[{"id":"${UUID.randomUUID()}","name":"Oil Filter","quantity":2,"unitPrice":30.00}],
              "totalAmount":160.00
            }
            """.trimIndent(),
        )

        SuppliesReservedHandler(useCase).handle(e)

        val slot = slot<RegisterQuoteRequest>()
        verify { useCase.createQuote(capture(slot), e.eventId) }
        val req = slot.captured
        assertEquals(orderId, req.orderId)
        assertEquals(reservationId, req.reservationId)
        assertEquals("John", req.customerName)
        assertEquals("john@example.com", req.customerEmail)
        assertEquals(1, req.services.size)
        assertEquals("Oil Change", req.services[0].name)
        assertEquals(0, BigDecimal("100.00").compareTo(req.services[0].price))
        assertEquals(1, req.supplies.size)
        assertEquals("Oil Filter", req.supplies[0].name)
        assertEquals(2, req.supplies[0].quantity)
        assertEquals(0, BigDecimal("30.00").compareTo(req.supplies[0].unitPrice))
        assertEquals(0, BigDecimal("160.00").compareTo(req.totalAmount))
    }
}
