package br.com.soat.quote.event

import br.com.soat.event.model.DomainEvent
import java.util.UUID

class PaymentFailedEvent(
    val orderId: UUID,
    val reservationId: UUID,
    val reason: String,
) : DomainEvent() {

    override val eventType = PAYMENT_FAILED

    companion object {
        const val PAYMENT_FAILED = "PaymentFailed"
    }
}
