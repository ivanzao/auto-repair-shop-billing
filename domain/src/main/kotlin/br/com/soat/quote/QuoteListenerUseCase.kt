package br.com.soat.quote

import br.com.soat.quote.event.QuoteEmailRequestedEvent
import br.com.soat.quote.model.request.RegisterQuoteRequest
import br.com.soat.quote.repository.QuoteApprovalTokenRepository
import br.com.soat.quote.repository.QuoteRepository
import br.com.soat.event.EventPublisher
import br.com.soat.event.repository.OutboxRepository
import br.com.soat.metric.MetricsPort
import br.com.soat.shared.repository.IdempotencyRepository
import br.com.soat.shared.repository.RepositoryTransactionHandler
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory

class QuoteListenerUseCase(
    private val quoteRepository: QuoteRepository,
    private val tokenRepository: QuoteApprovalTokenRepository,
    private val outbox: OutboxRepository,
    private val eventPublisher: EventPublisher,
    private val idempotency: IdempotencyRepository,
    private val tx: RepositoryTransactionHandler,
    private val baseUrl: String,
    private val approvalTtlDays: Long,
    private val metrics: MetricsPort,
) {
    private val logger = LoggerFactory.getLogger(QuoteListenerUseCase::class.java)

    fun createQuote(request: RegisterQuoteRequest, idempotencyId: UUID) {
        if (idempotency.exists(request.orderId, idempotencyId)) {
            logger.info(
                "Skipping already-processed SuppliesReserved",
                kv("orderId", request.orderId), kv("idempotencyId", idempotencyId),
            )
            return
        }
        if (quoteRepository.findByOrderId(request.orderId) != null) {
            logger.info("Quote already exists", kv("orderId", request.orderId))
            return
        }

        val lineItems = buildLineItems(request)
        val quote = Quote(
            orderId = request.orderId,
            reservationId = request.reservationId,
            customerName = request.customerName,
            customerEmail = request.customerEmail,
            lineItems = lineItems,
            totalAmount = request.totalAmount,
        )
        val token = QuoteApprovalToken(
            orderId = request.orderId,
            expiresAt = LocalDateTime.now().plusDays(approvalTtlDays),
        )
        val approvalUrl = "$baseUrl/v1/quotes/approve?token=${token.id}"
        val declineUrl = "$baseUrl/v1/quotes/decline?token=${token.id}"

        val event = tx.inTransaction {
            quoteRepository.save(quote)
            tokenRepository.save(token)
            idempotency.save(request.orderId, idempotencyId)
            outbox.save(
                QuoteEmailRequestedEvent(
                    orderId = request.orderId,
                    customer = QuoteEmailRequestedEvent.Customer(request.customerName, request.customerEmail),
                    totalAmount = request.totalAmount,
                    services = request.services.map {
                        QuoteEmailRequestedEvent.Service(it.name, it.price)
                    },
                    supplies = request.supplies.map {
                        QuoteEmailRequestedEvent.Supply(it.name, it.quantity, it.unitPrice)
                    },
                    approvalUrl = approvalUrl,
                    declineUrl = declineUrl,
                ),
            )
        }
        eventPublisher.publish(event)
        metrics.quoteCreated()

        logger.info(
            "Quote created and QuoteEmailRequested enqueued",
            kv("orderId", request.orderId), kv("reservationId", request.reservationId),
        )
    }

    private fun buildLineItems(request: RegisterQuoteRequest): List<Quote.LineItem> {
        val services = request.services.map { Quote.LineItem(it.name, it.price) }
        val supplies = request.supplies.map {
            Quote.LineItem(it.name, it.unitPrice.multiply(BigDecimal(it.quantity)))
        }
        return services + supplies
    }
}
