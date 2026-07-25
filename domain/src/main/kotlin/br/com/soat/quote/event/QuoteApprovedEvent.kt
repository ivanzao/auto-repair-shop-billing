package br.com.soat.quote.event

import br.com.soat.event.model.DomainEvent
import java.math.BigDecimal
import java.util.UUID

class QuoteApprovedEvent(
    val orderId: UUID,
    val customer: Customer,
    val totalAmount: BigDecimal,
    val checkoutUrl: String,
) : DomainEvent() {

    override val eventType = QUOTE_APPROVED

    data class Customer(val name: String, val email: String)

    companion object {
        const val QUOTE_APPROVED = "QuoteApproved"
    }
}
