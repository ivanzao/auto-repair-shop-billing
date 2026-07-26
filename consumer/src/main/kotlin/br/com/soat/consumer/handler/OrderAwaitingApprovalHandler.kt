package br.com.soat.consumer.handler

import br.com.soat.quote.QuoteListenerUseCase
import br.com.soat.quote.model.request.RegisterQuoteRequest
import br.com.soat.consumer.EventEnvelope
import br.com.soat.consumer.EventType
import br.com.soat.consumer.InboundEventHandler
import java.util.UUID

class OrderAwaitingApprovalHandler(
    private val quoteListenerUseCase: QuoteListenerUseCase,
) : InboundEventHandler {

    override val eventTypes = setOf(EventType.ORDER_AWAITING_APPROVAL)

    override fun handle(envelope: EventEnvelope) {
        val payload = envelope.payload
        val request = RegisterQuoteRequest(
            orderId = UUID.fromString(payload["orderId"].asText()),
            reservationId = UUID.fromString(payload["reservationId"].asText()),
            customerName = payload["customer"]["name"].asText(),
            customerEmail = payload["customer"]["email"].asText(),
            services = payload["services"].map {
                RegisterQuoteRequest.ServiceLine(it["name"].asText(), it["price"].decimalValue())
            },
            supplies = payload["supplies"].map {
                RegisterQuoteRequest.SupplyLine(
                    name = it["name"].asText(),
                    quantity = it["quantity"].asInt(),
                    unitPrice = it["unitPrice"].decimalValue(),
                )
            },
            totalAmount = payload["totalAmount"].decimalValue(),
        )
        quoteListenerUseCase.createQuote(request, envelope.eventId)
    }
}
