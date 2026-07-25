package br.com.soat.quote

import br.com.soat.payment.PaymentProviderPort
import br.com.soat.payment.model.PaymentDetails
import br.com.soat.payment.model.PaymentPreference
import br.com.soat.quote.event.QuoteApprovedEvent
import br.com.soat.quote.event.QuoteRejectedEvent
import br.com.soat.quote.exception.InvalidApprovalTokenException
import br.com.soat.quote.repository.QuoteApprovalTokenRepository
import br.com.soat.quote.repository.QuoteRepository
import br.com.soat.event.EventPublisher
import br.com.soat.event.OutboxEvent
import br.com.soat.event.model.DomainEvent
import br.com.soat.event.repository.OutboxRepository
import br.com.soat.shared.repository.RepositoryTransactionHandler
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class QuoteApprovalUseCaseTest {

    private class FakeQuoteRepository(seed: Quote) : QuoteRepository {
        val store = mutableMapOf(seed.orderId to seed)
        override fun findByOrderId(orderId: UUID): Quote? = store[orderId]
        override fun save(quote: Quote): Quote { store[quote.orderId] = quote; return quote }
        override fun update(quote: Quote): Quote { store[quote.orderId] = quote; return quote }
    }

    private class FakeTokenRepository(seed: QuoteApprovalToken?) : QuoteApprovalTokenRepository {
        val store = seed?.let { mutableMapOf(it.id to it) } ?: mutableMapOf()
        override fun save(token: QuoteApprovalToken): QuoteApprovalToken { store[token.id] = token; return token }
        override fun findById(id: UUID): QuoteApprovalToken? = store[id]
        override fun update(token: QuoteApprovalToken): QuoteApprovalToken { store[token.id] = token; return token }
    }

    private class FakePaymentProvider : PaymentProviderPort {
        var createPreferenceCalls = 0
        override fun createPreference(quote: Quote): PaymentPreference {
            createPreferenceCalls++
            return PaymentPreference("PREF-${quote.orderId}", "http://checkout/${quote.orderId}")
        }
        override fun getPayment(paymentId: String): PaymentDetails = throw UnsupportedOperationException()
        override fun refund(paymentId: String) = throw UnsupportedOperationException()
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

    private fun quote(orderId: UUID = UUID.randomUUID()) = Quote(
        orderId = orderId,
        reservationId = UUID.randomUUID(),
        customerName = "John",
        customerEmail = "john@example.com",
        lineItems = listOf(Quote.LineItem("Oil Change", BigDecimal("100.00"))),
        totalAmount = BigDecimal("100.00"),
    )

    private fun validToken(orderId: UUID) =
        QuoteApprovalToken(orderId = orderId, expiresAt = LocalDateTime.now().plusDays(5))

    @Test
    fun `approve marks token used, creates preference, sets quote APPROVED and returns initPoint`() {
        val q = quote()
        val token = validToken(q.orderId)
        val quoteRepo = FakeQuoteRepository(q)
        val tokenRepo = FakeTokenRepository(token)
        val provider = FakePaymentProvider()
        val useCase = QuoteApprovalUseCase(quoteRepo, tokenRepo, provider, FakeOutbox(), FakePublisher(), DirectTransaction)

        val initPoint = useCase.approve(token.id)

        assertEquals("http://checkout/${q.orderId}", initPoint)
        assertEquals(1, provider.createPreferenceCalls)
        assertEquals(QuoteStatus.APPROVED, quoteRepo.store[q.orderId]!!.status)
        assertEquals("PREF-${q.orderId}", quoteRepo.store[q.orderId]!!.preferenceId)
        assertEquals(false, tokenRepo.store[token.id]!!.isValid())
    }

    @Test
    fun `approve enqueues QuoteApproved carrying the checkout url and the customer`() {
        val q = quote()
        val token = validToken(q.orderId)
        val outbox = FakeOutbox()
        val publisher = FakePublisher()
        val useCase = QuoteApprovalUseCase(
            FakeQuoteRepository(q), FakeTokenRepository(token), FakePaymentProvider(),
            outbox, publisher, DirectTransaction,
        )

        val initPoint = useCase.approve(token.id)

        val event = outbox.saved as QuoteApprovedEvent
        assertEquals(q.orderId, event.orderId)
        assertEquals(initPoint, event.checkoutUrl)
        assertEquals("John", event.customer.name)
        assertEquals("john@example.com", event.customer.email)
        assertEquals(0, q.totalAmount.compareTo(event.totalAmount))
        assertEquals(1, publisher.published.size)
    }

    @Test
    fun `approve with an invalid token throws and changes nothing`() {
        val q = quote()
        val quoteRepo = FakeQuoteRepository(q)
        val provider = FakePaymentProvider()
        val useCase = QuoteApprovalUseCase(
            quoteRepo, FakeTokenRepository(null), provider, FakeOutbox(), FakePublisher(), DirectTransaction,
        )

        assertThrows(InvalidApprovalTokenException::class.java) { useCase.approve(UUID.randomUUID()) }
        assertEquals(0, provider.createPreferenceCalls)
        assertEquals(QuoteStatus.PENDING_APPROVAL, quoteRepo.store[q.orderId]!!.status)
    }

    @Test
    fun `approve with an already-used token throws and publishes nothing`() {
        val q = quote()
        val used = validToken(q.orderId).markAsUsed()
        val provider = FakePaymentProvider()
        val publisher = FakePublisher()
        val useCase = QuoteApprovalUseCase(
            FakeQuoteRepository(q), FakeTokenRepository(used), provider,
            FakeOutbox(), publisher, DirectTransaction,
        )

        assertThrows(InvalidApprovalTokenException::class.java) { useCase.approve(used.id) }
        assertEquals(0, provider.createPreferenceCalls)
        assertEquals(0, publisher.published.size)
    }

    @Test
    fun `decline sets quote REJECTED and enqueues QuoteRejected with reservationId`() {
        val q = quote()
        val token = validToken(q.orderId)
        val quoteRepo = FakeQuoteRepository(q)
        val outbox = FakeOutbox()
        val publisher = FakePublisher()
        val useCase = QuoteApprovalUseCase(
            quoteRepo, FakeTokenRepository(token), FakePaymentProvider(), outbox, publisher, DirectTransaction,
        )

        useCase.decline(token.id)

        assertEquals(QuoteStatus.REJECTED, quoteRepo.store[q.orderId]!!.status)
        val event = outbox.saved as QuoteRejectedEvent
        assertEquals(q.orderId, event.orderId)
        assertEquals(q.reservationId, event.reservationId)
        assertEquals(1, publisher.published.size)
    }

    @Test
    fun `decline with an invalid token does not publish`() {
        val q = quote()
        val publisher = FakePublisher()
        val useCase = QuoteApprovalUseCase(
            FakeQuoteRepository(q), FakeTokenRepository(null), FakePaymentProvider(),
            FakeOutbox(), publisher, DirectTransaction,
        )
        assertThrows(InvalidApprovalTokenException::class.java) { useCase.decline(UUID.randomUUID()) }
        assertEquals(0, publisher.published.size)
    }
}
