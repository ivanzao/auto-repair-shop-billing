package br.com.soat.quote.event

import br.com.soat.event.model.DomainEvent
import java.math.BigDecimal
import java.util.UUID

class QuoteEmailRequestedEvent(
    val orderId: UUID,
    val customer: Customer,
    val totalAmount: BigDecimal,
    val lineItems: List<LineItem>,
    val approvalUrl: String,
) : DomainEvent() {

    override val eventType = QUOTE_EMAIL_REQUESTED

    data class Customer(val name: String, val email: String)
    data class LineItem(val name: String, val price: BigDecimal)

    companion object {
        const val QUOTE_EMAIL_REQUESTED = "QuoteEmailRequested"
    }
}
