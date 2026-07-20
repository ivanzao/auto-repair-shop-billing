package br.com.soat.quote

import br.com.soat.IntegrationTest
import br.com.soat.quote.Quote
import br.com.soat.quote.QuoteApprovalToken
import br.com.soat.quote.QuoteStatus
import br.com.soat.quote.repository.QuoteApprovalTokenRepository
import br.com.soat.quote.repository.QuoteRepository
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

fun IntegrationTest.persistQuote(
    orderId: UUID = UUID.randomUUID(),
    reservationId: UUID = UUID.randomUUID(),
    status: QuoteStatus = QuoteStatus.PENDING_APPROVAL,
    paymentId: String? = null,
    totalAmount: BigDecimal = BigDecimal("160.00"),
): Quote {
    val quote = Quote(
        orderId = orderId,
        reservationId = reservationId,
        customerName = "John Doe",
        customerEmail = "john@example.com",
        lineItems = listOf(
            Quote.LineItem("Oil Change", BigDecimal("100.00")),
            Quote.LineItem("Oil Filter", BigDecimal("60.00")),
        ),
        totalAmount = totalAmount,
        status = status,
        paymentId = paymentId,
    )
    return get<QuoteRepository>().save(quote)
}

fun IntegrationTest.persistApprovalToken(orderId: UUID): QuoteApprovalToken =
    get<QuoteApprovalTokenRepository>().save(
        QuoteApprovalToken(orderId = orderId, expiresAt = LocalDateTime.now().plusDays(5)),
    )
