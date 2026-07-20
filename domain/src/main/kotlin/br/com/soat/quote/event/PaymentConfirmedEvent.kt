package br.com.soat.quote.event

import br.com.soat.event.model.DomainEvent
import java.math.BigDecimal
import java.util.UUID

class PaymentConfirmedEvent(
    val orderId: UUID,
    val paymentId: String,
    val amount: BigDecimal,
) : DomainEvent() {

    override val eventType = PAYMENT_CONFIRMED

    companion object {
        const val PAYMENT_CONFIRMED = "PaymentConfirmed"
    }
}
