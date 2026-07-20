package br.com.soat.consumer.handler

import br.com.soat.payment.PaymentUseCase
import br.com.soat.consumer.EventEnvelope
import br.com.soat.consumer.EventType
import br.com.soat.consumer.InboundEventHandler
import java.util.UUID

class ExecutionFailedHandler(
    private val paymentUseCase: PaymentUseCase,
) : InboundEventHandler {

    override val eventTypes = setOf(EventType.EXECUTION_FAILED)

    override fun handle(envelope: EventEnvelope) {
        val orderId = UUID.fromString(envelope.payload["orderId"].asText())
        val paymentId = envelope.payload["paymentId"].asText()
        paymentUseCase.refundPayment(orderId, paymentId)
    }
}
