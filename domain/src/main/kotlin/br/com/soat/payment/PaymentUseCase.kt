package br.com.soat.payment

import br.com.soat.payment.model.PaymentState
import br.com.soat.quote.QuoteStatus
import br.com.soat.quote.event.PaymentConfirmedEvent
import br.com.soat.quote.event.PaymentFailedEvent
import br.com.soat.quote.repository.QuoteRepository
import br.com.soat.event.EventPublisher
import br.com.soat.event.repository.OutboxRepository
import br.com.soat.shared.repository.RepositoryTransactionHandler
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory

class PaymentUseCase(
    private val quoteRepository: QuoteRepository,
    private val paymentProvider: PaymentProviderPort,
    private val outbox: OutboxRepository,
    private val eventPublisher: EventPublisher,
    private val tx: RepositoryTransactionHandler,
) {
    private val logger = LoggerFactory.getLogger(PaymentUseCase::class.java)

    fun handlePaymentUpdate(paymentId: String) {
        val details = paymentProvider.getPayment(paymentId)
        val quote = quoteRepository.findByOrderId(details.orderId) ?: run {
            logger.warn("No quote for payment notification", kv("orderId", details.orderId))
            return
        }
        if (quote.status == QuoteStatus.PAID || quote.status == QuoteStatus.PAYMENT_FAILED) {
            logger.info("Quote already terminal; idempotent skip", kv("orderId", quote.orderId), kv("status", quote.status))
            return
        }

        when (details.state) {
            PaymentState.APPROVED -> {
                val event = tx.inTransaction {
                    quoteRepository.update(quote.paid(paymentId))
                    outbox.save(PaymentConfirmedEvent(quote.orderId, paymentId, details.amount))
                }
                eventPublisher.publish(event)
                logger.info("Payment confirmed", kv("orderId", quote.orderId), kv("paymentId", paymentId))
            }

            PaymentState.REJECTED -> {
                val event = tx.inTransaction {
                    quoteRepository.update(quote.paymentFailed())
                    outbox.save(PaymentFailedEvent(quote.orderId, quote.reservationId, "payment_rejected"))
                }
                eventPublisher.publish(event)
                logger.info("Payment failed", kv("orderId", quote.orderId), kv("paymentId", paymentId))
            }

            PaymentState.PENDING ->
                logger.info("Payment pending; awaiting next notification", kv("paymentId", paymentId))
        }
    }

    fun refundPayment(orderId: UUID, paymentId: String) {
        val quote = quoteRepository.findByOrderId(orderId) ?: run {
            logger.warn("No quote for ExecutionFailed; ignoring", kv("orderId", orderId))
            return
        }
        if (quote.status == QuoteStatus.REFUNDED) {
            logger.info("Quote already refunded; idempotent skip", kv("orderId", orderId))
            return
        }

        paymentProvider.refund(paymentId)
        tx.inTransaction { quoteRepository.update(quote.refunded()) }
        logger.info("Payment refunded", kv("orderId", orderId), kv("paymentId", paymentId))
    }
}
