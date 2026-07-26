package br.com.soat.quote

import br.com.soat.IntegrationTest
import br.com.soat.quote.QuoteStatus
import br.com.soat.quote.repository.QuoteApprovalTokenRepository
import br.com.soat.quote.repository.QuoteRepository
import java.math.BigDecimal
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuoteCreationIntegrationTest : IntegrationTest() {

    private val quoteRepository: QuoteRepository by lazy { get<QuoteRepository>() }
    private val tokenRepository: QuoteApprovalTokenRepository by lazy { get<QuoteApprovalTokenRepository>() }

    @Test
    fun `OrderAwaitingApproval creates a pending quote and publishes QuoteEmailRequested`() {
        sendSagaMessage(orderAwaitingApprovalEnvelope(), "OrderAwaitingApproval")

        val published = waitForPublishedEvent("QuoteEmailRequested")
        assertEquals(CONTRACT_ORDER_ID.toString(), published.payload["orderId"].asText())
        val services = published.payload["services"]
        assertEquals(1, services.size())
        assertEquals("Troca de oleo", services[0]["name"].asText())

        val supplies = published.payload["supplies"]
        assertEquals(1, supplies.size())
        assertEquals("Filtro de oleo", supplies[0]["name"].asText())
        assertEquals(2, supplies[0]["quantity"].asInt())
        assertEquals(0, BigDecimal("30.00").compareTo(supplies[0]["unitPrice"].decimalValue()))

        val approvalUrl = published.payload["approvalUrl"].asText()
        assertTrue(approvalUrl.contains("/v1/quotes/approve?token="), "approvalUrl: $approvalUrl")

        val declineUrl = published.payload["declineUrl"].asText()
        assertTrue(declineUrl.contains("/v1/quotes/decline?token="), "declineUrl: $declineUrl")
        assertEquals(
            approvalUrl.substringAfter("token="),
            declineUrl.substringAfter("token="),
            "os dois links têm que usar o mesmo token",
        )

        val quote = quoteRepository.findByOrderId(CONTRACT_ORDER_ID)!!
        assertEquals(CONTRACT_RESERVATION_ID, quote.reservationId)
        assertEquals(QuoteStatus.PENDING_APPROVAL, quote.status)
        assertEquals(2, quote.lineItems.size)
        assertEquals(0, BigDecimal("160.00").compareTo(quote.totalAmount))

        val tokenId = UUID.fromString(approvalUrl.substringAfter("token="))
        assertNotNull(tokenRepository.findById(tokenId))
    }

    @Test
    fun `a redelivered OrderAwaitingApproval does not create a second quote`() {
        val envelope = orderAwaitingApprovalEnvelope()

        sendSagaMessage(envelope, "OrderAwaitingApproval")
        sendSagaMessage(envelope, "OrderAwaitingApproval")

        waitForPublishedEvent("QuoteEmailRequested")
        await { quoteRepository.findByOrderId(CONTRACT_ORDER_ID) != null }

        Thread.sleep(1500)
        assertEquals(QuoteStatus.PENDING_APPROVAL, quoteRepository.findByOrderId(CONTRACT_ORDER_ID)!!.status)
        assertEquals(0, sagaQueueDepth())
    }
}
