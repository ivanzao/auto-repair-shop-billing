package br.com.soat.payment

import br.com.soat.payment.model.PaymentDetails
import br.com.soat.payment.model.PaymentPreference
import br.com.soat.payment.model.PaymentState
import br.com.soat.quote.Quote
import br.com.soat.quote.QuoteStatus
import br.com.soat.quote.event.PaymentConfirmedEvent
import br.com.soat.quote.event.PaymentFailedEvent
import br.com.soat.quote.repository.QuoteRepository
import br.com.soat.event.EventPublisher
import br.com.soat.event.OutboxEvent
import br.com.soat.event.model.DomainEvent
import br.com.soat.event.repository.OutboxRepository
import br.com.soat.metric.RecordingMetrics
import br.com.soat.shared.repository.RepositoryTransactionHandler
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PaymentUseCaseTest {

    private val metrics = RecordingMetrics()

    private class FakeQuoteRepository(seed: Quote) : QuoteRepository {
        val store = mutableMapOf(seed.orderId to seed)
        var updates = 0
        override fun findByOrderId(orderId: UUID): Quote? = store[orderId]
        override fun save(quote: Quote): Quote { store[quote.orderId] = quote; return quote }
        override fun update(quote: Quote): Quote { updates++; store[quote.orderId] = quote; return quote }
    }

    private class StubPaymentProvider(
        private val details: PaymentDetails = PaymentDetails("PAY-1", UUID.randomUUID(), BigDecimal.ZERO, PaymentState.PENDING),
    ) : PaymentProviderPort {
        val refunds = mutableListOf<String>()
        override fun createPreference(quote: Quote): PaymentPreference = throw UnsupportedOperationException()
        override fun getPayment(paymentId: String): PaymentDetails = details
        override fun refund(paymentId: String) { refunds += paymentId }
    }

    private class FakeOutbox : OutboxRepository {
        var saved: DomainEvent? = null
        override fun save(event: DomainEvent): OutboxEvent {
            saved = event
            return OutboxEvent(event.eventId, event.eventType, event.eventVersion, event.occurredAt, "{}")
        }
        override fun findPendingOlderThan(age: Duration, limit: Int): List<OutboxEvent> = emptyList()
        override fun delete(eventId: UUID) {}
    }

    private class FakePublisher : EventPublisher {
        val published = mutableListOf<OutboxEvent>()
        override fun publish(event: OutboxEvent) { published += event }
    }

    private object DirectTransaction : RepositoryTransactionHandler {
        override fun <T> inTransaction(function: () -> T): T = function()
    }

    private fun quote(status: QuoteStatus = QuoteStatus.APPROVED, orderId: UUID = UUID.randomUUID()) = Quote(
        orderId = orderId,
        reservationId = UUID.randomUUID(),
        customerName = "John",
        customerEmail = "john@example.com",
        lineItems = listOf(Quote.LineItem("Oil Change", BigDecimal("100.00"))),
        totalAmount = BigDecimal("100.00"),
        status = status,
    )

    private fun details(orderId: UUID, state: PaymentState, amount: BigDecimal = BigDecimal("100.00")) =
        PaymentDetails("PAY-1", orderId, amount, state)

    @Test
    fun `approved payment marks quote PAID and enqueues PaymentConfirmed`() {
        val q = quote()
        val repo = FakeQuoteRepository(q)
        val outbox = FakeOutbox()
        val publisher = FakePublisher()
        val useCase = PaymentUseCase(
            repo, StubPaymentProvider(details(q.orderId, PaymentState.APPROVED, BigDecimal("209.90"))),
            outbox, publisher, DirectTransaction, metrics,
        )

        useCase.handlePaymentUpdate("PAY-1")

        assertEquals(QuoteStatus.PAID, repo.store[q.orderId]!!.status)
        assertEquals("PAY-1", repo.store[q.orderId]!!.paymentId)
        val event = outbox.saved as PaymentConfirmedEvent
        assertEquals(q.orderId, event.orderId)
        assertEquals("PAY-1", event.paymentId)
        assertEquals(0, BigDecimal("209.90").compareTo(event.amount))
        assertEquals(1, publisher.published.size)
        assertEquals(listOf(PaymentState.APPROVED), metrics.payments)
        assertEquals(listOf(QuoteStatus.PAID), metrics.quoteStatuses)
    }

    @Test
    fun `rejected payment marks quote PAYMENT_FAILED and enqueues PaymentFailed with reservationId`() {
        val q = quote()
        val repo = FakeQuoteRepository(q)
        val outbox = FakeOutbox()
        val useCase = PaymentUseCase(
            repo, StubPaymentProvider(details(q.orderId, PaymentState.REJECTED)),
            outbox, FakePublisher(), DirectTransaction, metrics,
        )

        useCase.handlePaymentUpdate("PAY-1")

        assertEquals(QuoteStatus.PAYMENT_FAILED, repo.store[q.orderId]!!.status)
        val event = outbox.saved as PaymentFailedEvent
        assertEquals(q.orderId, event.orderId)
        assertEquals(q.reservationId, event.reservationId)
        assertEquals("payment_rejected", event.reason)
        assertEquals(listOf(PaymentState.REJECTED), metrics.payments)
        assertEquals(listOf(QuoteStatus.PAYMENT_FAILED), metrics.quoteStatuses)
    }

    @Test
    fun `pending payment does nothing`() {
        val q = quote()
        val repo = FakeQuoteRepository(q)
        val outbox = FakeOutbox()
        val useCase = PaymentUseCase(
            repo, StubPaymentProvider(details(q.orderId, PaymentState.PENDING)),
            outbox, FakePublisher(), DirectTransaction, metrics,
        )

        useCase.handlePaymentUpdate("PAY-1")

        assertEquals(QuoteStatus.APPROVED, repo.store[q.orderId]!!.status)
        assertEquals(0, repo.updates)
        assertNull(outbox.saved)
        assertEquals(listOf(PaymentState.PENDING), metrics.payments)
        assertEquals(emptyList<QuoteStatus>(), metrics.quoteStatuses)
    }

    @Test
    fun `a payment for an already-paid quote is idempotent`() {
        val q = quote(status = QuoteStatus.PAID)
        val repo = FakeQuoteRepository(q)
        val publisher = FakePublisher()
        val useCase = PaymentUseCase(
            repo, StubPaymentProvider(details(q.orderId, PaymentState.APPROVED)),
            FakeOutbox(), publisher, DirectTransaction, metrics,
        )

        useCase.handlePaymentUpdate("PAY-1")

        assertEquals(0, repo.updates)
        assertEquals(0, publisher.published.size)
        assertEquals(emptyList<PaymentState>(), metrics.payments, "quote terminal nao gera metrica de pagamento")
    }

    @Test
    fun `refundPayment refunds a paid quote and marks it REFUNDED`() {
        val q = quote(status = QuoteStatus.PAID)
        val repo = FakeQuoteRepository(q)
        val provider = StubPaymentProvider()
        val useCase = PaymentUseCase(repo, provider, FakeOutbox(), FakePublisher(), DirectTransaction, metrics)

        useCase.refundPayment(q.orderId, "PAY-1")

        assertEquals(listOf("PAY-1"), provider.refunds)
        assertEquals(QuoteStatus.REFUNDED, repo.store[q.orderId]!!.status)
    }

    @Test
    fun `refundPayment is idempotent for an already-refunded quote`() {
        val q = quote(status = QuoteStatus.REFUNDED)
        val repo = FakeQuoteRepository(q)
        val provider = StubPaymentProvider()
        val useCase = PaymentUseCase(repo, provider, FakeOutbox(), FakePublisher(), DirectTransaction, metrics)

        useCase.refundPayment(q.orderId, "PAY-1")

        assertEquals(0, provider.refunds.size, "não pode estornar de novo")
        assertEquals(0, repo.updates)
    }
}
