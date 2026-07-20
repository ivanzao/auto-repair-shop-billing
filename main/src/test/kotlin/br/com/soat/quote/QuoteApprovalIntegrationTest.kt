package br.com.soat.quote

import br.com.soat.IntegrationTest
import br.com.soat.quote.QuoteStatus
import br.com.soat.quote.repository.QuoteRepository
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuoteApprovalIntegrationTest : IntegrationTest() {

    private val quoteRepository: QuoteRepository by lazy { get<QuoteRepository>() }

    @Test
    fun `approve redirects 302 to the checkout and marks the quote APPROVED`() {
        val quote = persistQuote()
        val token = persistApprovalToken(quote.orderId)

        val response = http.get("/v1/quotes/approve?token=${token.id}")

        assertEquals(302, response.statusCode())
        val location = response.headers().firstValue("location").orElse("")
        assertTrue(location.contains("fake-checkout?order=${quote.orderId}"), "location: $location")

        val updated = quoteRepository.findByOrderId(quote.orderId)!!
        assertEquals(QuoteStatus.APPROVED, updated.status)
        assertNotNull(updated.preferenceId)
    }

    @Test
    fun `decline marks the quote REJECTED and publishes QuoteRejected`() {
        val quote = persistQuote()
        val token = persistApprovalToken(quote.orderId)

        val response = http.get("/v1/quotes/decline?token=${token.id}")
        assertEquals(200, response.statusCode())

        val updated = quoteRepository.findByOrderId(quote.orderId)!!
        assertEquals(QuoteStatus.REJECTED, updated.status)

        val published = waitForPublishedEvent("QuoteRejected")
        assertEquals(quote.orderId.toString(), published.payload["orderId"].asText())
        assertEquals(quote.reservationId.toString(), published.payload["reservationId"].asText())
    }

    @Test
    fun `an unknown token yields 400`() {
        val response = http.get("/v1/quotes/approve?token=${UUID.randomUUID()}")
        assertEquals(400, response.statusCode())
    }
}
