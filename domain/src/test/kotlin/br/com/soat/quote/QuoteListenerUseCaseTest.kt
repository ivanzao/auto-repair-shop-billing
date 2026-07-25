package br.com.soat.quote

import br.com.soat.quote.event.QuoteEmailRequestedEvent
import br.com.soat.quote.model.request.RegisterQuoteRequest
import br.com.soat.quote.repository.QuoteApprovalTokenRepository
import br.com.soat.quote.repository.QuoteRepository
import br.com.soat.event.EventPublisher
import br.com.soat.event.OutboxEvent
import br.com.soat.event.model.DomainEvent
import br.com.soat.event.repository.OutboxRepository
import br.com.soat.shared.repository.IdempotencyRepository
import br.com.soat.metric.RecordingMetrics
import br.com.soat.shared.repository.RepositoryTransactionHandler
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuoteListenerUseCaseTest {

    private class FakeQuoteRepository : QuoteRepository {
        val store = mutableMapOf<UUID, Quote>()
        var saves = 0
        override fun findByOrderId(orderId: UUID): Quote? = store[orderId]
        override fun save(quote: Quote): Quote { saves++; store[quote.orderId] = quote; return quote }
        override fun update(quote: Quote): Quote { store[quote.orderId] = quote; return quote }
    }

    private class FakeTokenRepository : QuoteApprovalTokenRepository {
        val store = mutableMapOf<UUID, QuoteApprovalToken>()
        override fun save(token: QuoteApprovalToken): QuoteApprovalToken { store[token.id] = token; return token }
        override fun findById(id: UUID): QuoteApprovalToken? = store[id]
        override fun update(token: QuoteApprovalToken): QuoteApprovalToken { store[token.id] = token; return token }
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

    private class FakeIdempotency : IdempotencyRepository {
        val seen = mutableSetOf<Pair<UUID, UUID>>()
        override fun exists(entityId: UUID, idempotencyId: UUID) = (entityId to idempotencyId) in seen
        override fun save(entityId: UUID, idempotencyId: UUID) { seen += entityId to idempotencyId }
    }

    private object DirectTransaction : RepositoryTransactionHandler {
        override fun <T> inTransaction(function: () -> T): T = function()
    }

    private val quoteRepository = FakeQuoteRepository()
    private val tokenRepository = FakeTokenRepository()
    private val outbox = FakeOutbox()
    private val publisher = FakePublisher()
    private val idempotency = FakeIdempotency()

    private val metrics = RecordingMetrics()

    private val useCase = QuoteListenerUseCase(
        quoteRepository, tokenRepository, outbox, publisher, idempotency, DirectTransaction,
        baseUrl = "http://base", approvalTtlDays = 5, metrics = metrics,
    )

    private fun request(orderId: UUID = UUID.randomUUID()) = RegisterQuoteRequest(
        orderId = orderId,
        reservationId = UUID.randomUUID(),
        customerName = "John",
        customerEmail = "john@example.com",
        services = listOf(RegisterQuoteRequest.ServiceLine("Oil Change", BigDecimal("100.00"))),
        supplies = listOf(RegisterQuoteRequest.SupplyLine("Oil Filter", 2, BigDecimal("30.00"))),
        totalAmount = BigDecimal("160.00"),
    )

    @Test
    fun `creates a pending quote persisting the reservationId`() {
        val req = request()
        useCase.createQuote(req, UUID.randomUUID())

        val quote = quoteRepository.store[req.orderId]!!
        assertEquals(req.reservationId, quote.reservationId)
        assertEquals(QuoteStatus.PENDING_APPROVAL, quote.status)
        assertEquals(0, BigDecimal("160.00").compareTo(quote.totalAmount))
    }

    @Test
    fun `derives line items from services and priced supplies`() {
        val req = request()
        useCase.createQuote(req, UUID.randomUUID())

        val items = quoteRepository.store[req.orderId]!!.lineItems
        assertEquals(2, items.size)
        assertEquals("Oil Change", items[0].name)
        assertEquals(0, BigDecimal("100.00").compareTo(items[0].price))
        assertEquals("Oil Filter", items[1].name)
        assertEquals(0, BigDecimal("60.00").compareTo(items[1].price))
    }

    @Test
    fun `saves an approval token and enqueues QuoteEmailRequested with the approval url`() {
        val req = request()
        useCase.createQuote(req, UUID.randomUUID())

        val token = tokenRepository.store.values.single()
        assertEquals(req.orderId, token.orderId)

        val event = outbox.saved as QuoteEmailRequestedEvent
        assertEquals(req.orderId, event.orderId)
        assertEquals("http://base/v1/quotes/approve?token=${token.id}", event.approvalUrl)
        assertEquals(2, event.lineItems.size)
        assertEquals(1, publisher.published.size)
    }

    @Test
    fun `a redelivered event is skipped entirely`() {
        val req = request()
        val eventId = UUID.randomUUID()
        useCase.createQuote(req, eventId)
        useCase.createQuote(req, eventId)

        assertEquals(1, quoteRepository.saves, "a segunda entrega não pode criar a quote de novo")
        assertEquals(1, publisher.published.size)
    }

    @Test
    fun `an already-existing quote is not recreated`() {
        val req = request()
        useCase.createQuote(req, UUID.randomUUID())
        useCase.createQuote(req, UUID.randomUUID())

        assertEquals(1, quoteRepository.saves)
        assertNotNull(quoteRepository.store[req.orderId])
        assertTrue(publisher.published.size == 1)
    }

    @Test
    fun `counts a created quote once and never on the idempotent replay`() {
        val req = request()
        val idempotencyId = UUID.randomUUID()

        useCase.createQuote(req, idempotencyId)
        useCase.createQuote(req, idempotencyId)

        assertEquals(1, metrics.quotesCreated)
    }
}
