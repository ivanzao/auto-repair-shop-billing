package br.com.soat.payment

import br.com.soat.quote.persistQuote
import br.com.soat.IntegrationTest
import br.com.soat.quote.QuoteStatus
import br.com.soat.quote.repository.QuoteRepository
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PaymentWebhookIntegrationTest : IntegrationTest() {

    private val quoteRepository: QuoteRepository by lazy { get<QuoteRepository>() }
    private val webhookSecret = "test-webhook-secret"

    private fun signatureHeaders(dataId: String, requestId: String = "req-1", ts: String = "1700000000"): Map<String, String> {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(webhookSecret.toByteArray(), "HmacSHA256"))
        val v1 = mac.doFinal("id:$dataId;request-id:$requestId;ts:$ts;".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return mapOf("x-request-id" to requestId, "x-signature" to "ts=$ts,v1=$v1")
    }

    @Test
    fun `valid webhook for an approved payment marks quote PAID and publishes PaymentConfirmed`() {

        val quote = persistQuote(status = QuoteStatus.APPROVED)
        val paymentId = quote.orderId.toString()

        val response = http.post(
            "/v1/webhooks/mercadopago?data.id=$paymentId",
            body = """{"type":"payment","data":{"id":"$paymentId"}}""",
            headers = signatureHeaders(paymentId),
        )

        assertEquals(200, response.statusCode())
        assertEquals(QuoteStatus.PAID, quoteRepository.findByOrderId(quote.orderId)!!.status)

        val published = waitForPublishedEvent("PaymentConfirmed")
        assertEquals(quote.orderId.toString(), published.payload["orderId"].asText())
        assertEquals(paymentId, published.payload["paymentId"].asText())
    }

    @Test
    fun `a non-payment notification (merchant_order) is acked with 200 and ignored`() {
        val quote = persistQuote(status = QuoteStatus.APPROVED)
        val dataId = quote.orderId.toString()

        val response = http.post(
            "/v1/webhooks/mercadopago?data.id=$dataId&type=merchant_order",
            body = """{"type":"merchant_order","data":{"id":"$dataId"}}""",
            headers = signatureHeaders(dataId),
        )

        assertEquals(200, response.statusCode())
        assertEquals(QuoteStatus.APPROVED, quoteRepository.findByOrderId(quote.orderId)!!.status)
    }

    @Test
    fun `a signature that does not match is accepted and still settles the quote`() {
        val quote = persistQuote(status = QuoteStatus.APPROVED)
        val paymentId = quote.orderId.toString()

        val response = http.post(
            "/v1/webhooks/mercadopago?data.id=$paymentId",
            body = "{}",
            headers = mapOf("x-request-id" to "req-1", "x-signature" to "ts=1700000000,v1=deadbeef"),
        )

        assertEquals(200, response.statusCode())
        assertEquals(QuoteStatus.PAID, quoteRepository.findByOrderId(quote.orderId)!!.status)

        val published = waitForPublishedEvent("PaymentConfirmed")
        assertEquals(quote.orderId.toString(), published.payload["orderId"].asText())
    }

    @Test
    fun `a webhook without signature is accepted`() {
        val response = http.post("/v1/webhooks/mercadopago?data.id=${UUID.randomUUID()}", body = "{}")
        assertEquals(200, response.statusCode())
    }
}
