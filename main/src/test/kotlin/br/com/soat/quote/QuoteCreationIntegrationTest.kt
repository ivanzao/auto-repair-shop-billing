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
    fun `SuppliesReserved creates a pending quote and publishes QuoteEmailRequested`() {
        val orderId = UUID.randomUUID()
        val reservationId = UUID.randomUUID()

        sendSagaMessage(suppliesReservedEnvelope(orderId, reservationId), "SuppliesReserved")

        val published = waitForPublishedEvent("QuoteEmailRequested")
        assertEquals(orderId.toString(), published.payload["orderId"].asText())
        assertEquals(2, published.payload["lineItems"].size())

        val approvalUrl = published.payload["approvalUrl"].asText()
        assertTrue(approvalUrl.contains("/v1/quotes/approve?token="), "approvalUrl: $approvalUrl")

        val quote = quoteRepository.findByOrderId(orderId)!!
        assertEquals(reservationId, quote.reservationId)
        assertEquals(QuoteStatus.PENDING_APPROVAL, quote.status)
        assertEquals(2, quote.lineItems.size)
        assertEquals(0, BigDecimal("160.00").compareTo(quote.totalAmount))

        val tokenId = UUID.fromString(approvalUrl.substringAfter("token="))
        assertNotNull(tokenRepository.findById(tokenId))
    }

    @Test
    fun `a redelivered SuppliesReserved does not create a second quote`() {
        val orderId = UUID.randomUUID()
        val reservationId = UUID.randomUUID()
        val envelope = suppliesReservedEnvelope(orderId, reservationId)

        sendSagaMessage(envelope, "SuppliesReserved")
        sendSagaMessage(envelope, "SuppliesReserved")

        waitForPublishedEvent("QuoteEmailRequested")
        await { quoteRepository.findByOrderId(orderId) != null }

        Thread.sleep(1500)
        assertEquals(QuoteStatus.PENDING_APPROVAL, quoteRepository.findByOrderId(orderId)!!.status)
        assertEquals(0, sagaQueueDepth())
    }
}
