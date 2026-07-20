package br.com.soat.quote.event

import br.com.soat.event.model.DomainEvent
import java.util.UUID

class QuoteRejectedEvent(
    val orderId: UUID,
    val reservationId: UUID,
) : DomainEvent() {

    override val eventType = QUOTE_REJECTED

    companion object {
        const val QUOTE_REJECTED = "QuoteRejected"
    }
}
