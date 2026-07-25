package br.com.soat.quote.event

import br.com.soat.event.model.DomainEvent
import java.math.BigDecimal
import java.util.UUID

class QuoteEmailRequestedEvent(
    val orderId: UUID,
    val customer: Customer,
    val totalAmount: BigDecimal,
    val services: List<Service>,
    val supplies: List<Supply>,
    val approvalUrl: String,
    val declineUrl: String,
) : DomainEvent() {

    override val eventType = QUOTE_EMAIL_REQUESTED

    data class Customer(val name: String, val email: String)
    data class Service(val name: String, val price: BigDecimal)
    data class Supply(val name: String, val quantity: Int, val unitPrice: BigDecimal)

    companion object {
        const val QUOTE_EMAIL_REQUESTED = "QuoteEmailRequested"
    }
}
