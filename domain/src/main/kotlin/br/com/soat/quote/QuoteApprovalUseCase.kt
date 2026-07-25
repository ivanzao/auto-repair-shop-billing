package br.com.soat.quote

import br.com.soat.payment.PaymentProviderPort
import br.com.soat.quote.event.QuoteApprovedEvent
import br.com.soat.quote.event.QuoteRejectedEvent
import br.com.soat.quote.exception.InvalidApprovalTokenException
import br.com.soat.quote.exception.QuoteNotFoundException
import br.com.soat.quote.repository.QuoteApprovalTokenRepository
import br.com.soat.quote.repository.QuoteRepository
import br.com.soat.event.EventPublisher
import br.com.soat.event.repository.OutboxRepository
import br.com.soat.shared.repository.RepositoryTransactionHandler
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory

class QuoteApprovalUseCase(
    private val quoteRepository: QuoteRepository,
    private val tokenRepository: QuoteApprovalTokenRepository,
    private val paymentProvider: PaymentProviderPort,
    private val outbox: OutboxRepository,
    private val eventPublisher: EventPublisher,
    private val tx: RepositoryTransactionHandler,
) {
    private val logger = LoggerFactory.getLogger(QuoteApprovalUseCase::class.java)

    fun approve(tokenId: UUID): String {
        val token = validToken(tokenId)
        val quote = quoteRepository.findByOrderId(token.orderId)
            ?: throw QuoteNotFoundException(token.orderId)

        val preference = paymentProvider.createPreference(quote)

        val event = tx.inTransaction {
            tokenRepository.update(token.markAsUsed())
            quoteRepository.update(quote.approved(preference.preferenceId))
            outbox.save(
                QuoteApprovedEvent(
                    orderId = quote.orderId,
                    customer = QuoteApprovedEvent.Customer(quote.customerName, quote.customerEmail),
                    totalAmount = quote.totalAmount,
                    checkoutUrl = preference.initPoint,
                ),
            )
        }
        eventPublisher.publish(event)
        logger.info("Quote approved", kv("orderId", quote.orderId), kv("preferenceId", preference.preferenceId))
        return preference.initPoint
    }

    fun decline(tokenId: UUID) {
        val token = validToken(tokenId)
        val quote = quoteRepository.findByOrderId(token.orderId)
            ?: throw QuoteNotFoundException(token.orderId)

        val event = tx.inTransaction {
            tokenRepository.update(token.markAsUsed())
            quoteRepository.update(quote.rejected())
            outbox.save(QuoteRejectedEvent(quote.orderId, quote.reservationId))
        }
        eventPublisher.publish(event)
        logger.info("Quote declined", kv("orderId", quote.orderId))
    }

    private fun validToken(tokenId: UUID) =
        tokenRepository.findById(tokenId)?.takeIf { it.isValid() }
            ?: throw InvalidApprovalTokenException(tokenId)
}
