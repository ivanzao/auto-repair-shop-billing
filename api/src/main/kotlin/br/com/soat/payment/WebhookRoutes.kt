package br.com.soat.payment

import br.com.soat.metric.MetricsPort
import br.com.soat.metric.WebhookResult
import br.com.soat.payment.PaymentUseCase
import br.com.soat.payment.WebhookSignatureValidator
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.koin.core.Koin

fun Application.webhookRoutes(koin: Koin) {
    val paymentUseCase = koin.inject<PaymentUseCase>().value
    val signatureValidator = koin.inject<WebhookSignatureValidator>().value
    val metrics = koin.inject<MetricsPort>().value

    routing {
        post("/v1/webhooks/mercadopago") {
            call.receiveText()

            val type = call.request.queryParameters["type"] ?: call.request.queryParameters["topic"]
            if (type != null && type != "payment") {
                metrics.webhookReceived(WebhookResult.IGNORED_TYPE)
                call.respond(HttpStatusCode.OK)
                return@post
            }

            val dataId = call.request.queryParameters["data.id"] ?: call.request.queryParameters["id"]
            val requestId = call.request.headers["x-request-id"] ?: ""
            val signature = call.request.headers["x-signature"] ?: ""

            if (dataId == null || !signatureValidator.isValid(dataId, requestId, signature)) {
                metrics.webhookReceived(WebhookResult.INVALID_SIGNATURE)
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            paymentUseCase.handlePaymentUpdate(dataId)
            metrics.webhookReceived(WebhookResult.ACCEPTED)
            call.respond(HttpStatusCode.OK)
        }
    }
}
