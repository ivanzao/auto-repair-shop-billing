package br.com.soat.payment

import br.com.soat.payment.PaymentProviderPort
import br.com.soat.payment.model.PaymentDetails
import br.com.soat.payment.model.PaymentPreference
import br.com.soat.payment.model.PaymentState
import br.com.soat.quote.Quote
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.util.UUID
import kotlinx.coroutines.runBlocking

class MercadoPagoPaymentAdapter(
    private val client: HttpClient,
    private val apiUrl: String,
    private val accessToken: String,
    private val backUrlBase: String,
    private val mapper: ObjectMapper,
) : PaymentProviderPort {

    override fun createPreference(quote: Quote): PaymentPreference = runBlocking {
        val body = mapper.writeValueAsString(
            mapOf(
                "external_reference" to quote.orderId.toString(),
                "items" to quote.lineItems.map {
                    mapOf("title" to it.name, "quantity" to 1, "unit_price" to it.price)
                },
                "back_urls" to mapOf(
                    "success" to "$backUrlBase/v1/quotes/approve",
                    "failure" to "$backUrlBase/v1/quotes/decline",
                    "pending" to "$backUrlBase/v1/quotes/approve",
                ),
            ),
        )
        val response = client.post("$apiUrl/checkout/preferences") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val node = mapper.readTree(response.bodyAsText())
        PaymentPreference(
            preferenceId = node["id"].asText(),
            initPoint = node["init_point"].asText(),
        )
    }

    override fun getPayment(paymentId: String): PaymentDetails = runBlocking {
        val response = client.get("$apiUrl/v1/payments/$paymentId") { bearerAuth(accessToken) }
        val node = mapper.readTree(response.bodyAsText())
        PaymentDetails(
            paymentId = paymentId,
            orderId = UUID.fromString(node["external_reference"].asText()),
            amount = node["transaction_amount"].decimalValue(),
            state = when (node["status"].asText()) {
                "approved" -> PaymentState.APPROVED
                "rejected", "cancelled" -> PaymentState.REJECTED
                else -> PaymentState.PENDING
            },
        )
    }

    override fun refund(paymentId: String) {
        runBlocking {
            client.post("$apiUrl/v1/payments/$paymentId/refunds") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
        }
    }
}
