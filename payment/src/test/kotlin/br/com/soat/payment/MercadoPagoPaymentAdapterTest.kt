package br.com.soat.payment

import br.com.soat.payment.model.PaymentState
import br.com.soat.quote.Quote
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.math.BigDecimal
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MercadoPagoPaymentAdapterTest {

    private val mapper = ObjectMapper()
    private val requests = mutableListOf<HttpRequestData>()

    private fun adapter(
        responder: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): MercadoPagoPaymentAdapter {
        val engine = MockEngine { request ->
            requests += request
            responder(request)
        }
        return MercadoPagoPaymentAdapter(
            client = HttpClient(engine),
            apiUrl = "https://api.mercadopago.com",
            accessToken = "TEST-TOKEN",
            backUrlBase = "https://billing.example.com",
            mapper = mapper,
        )
    }

    private fun quote(orderId: UUID) = Quote(
        orderId = orderId,
        reservationId = UUID.randomUUID(),
        customerName = "John",
        customerEmail = "john@example.com",
        lineItems = listOf(
            Quote.LineItem("Oil Change", BigDecimal("100.00")),
            Quote.LineItem("Oil Filter", BigDecimal("60.00")),
        ),
        totalAmount = BigDecimal("160.00"),
    )

    @Test
    fun `createPreference posts external_reference and parses id and init_point`() {
        val orderId = UUID.randomUUID()
        val adapter = adapter {
            respond(
                content = """{"id":"PREF-123","init_point":"https://mp/checkout/PREF-123"}""",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val preference = adapter.createPreference(quote(orderId))

        assertEquals("PREF-123", preference.preferenceId)
        assertEquals("https://mp/checkout/PREF-123", preference.initPoint)

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/checkout/preferences", request.url.encodedPath)
        assertEquals("Bearer TEST-TOKEN", request.headers[HttpHeaders.Authorization])
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""external_reference":"$orderId""""), body)
        assertTrue(body.contains(""""title":"Oil Change""""), body)
    }

    @Test
    fun `getPayment maps approved status`() {
        val orderId = UUID.randomUUID()
        val adapter = adapter {
            respond(
                content = """{"status":"approved","transaction_amount":209.90,"external_reference":"$orderId"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val details = adapter.getPayment("PAY-1")

        assertEquals("PAY-1", details.paymentId)
        assertEquals(orderId, details.orderId)
        assertEquals(PaymentState.APPROVED, details.state)
        assertEquals(0, BigDecimal("209.90").compareTo(details.amount))
        assertEquals("/v1/payments/PAY-1", requests.single().url.encodedPath)
    }

    @Test
    fun `getPayment maps rejected and cancelled to REJECTED and others to PENDING`() {
        val orderId = UUID.randomUUID()
        fun state(status: String): PaymentState {
            requests.clear()
            return adapter {
                respond(
                    content = """{"status":"$status","transaction_amount":10.00,"external_reference":"$orderId"}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }.getPayment("PAY-X").state
        }

        assertEquals(PaymentState.REJECTED, state("rejected"))
        assertEquals(PaymentState.REJECTED, state("cancelled"))
        assertEquals(PaymentState.PENDING, state("in_process"))
    }

    @Test
    fun `refund posts to the refunds endpoint`() {
        val adapter = adapter {
            respond(content = """{"id":9999,"status":"approved"}""", status = HttpStatusCode.Created)
        }

        adapter.refund("PAY-1")

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/v1/payments/PAY-1/refunds", request.url.encodedPath)
    }
}
