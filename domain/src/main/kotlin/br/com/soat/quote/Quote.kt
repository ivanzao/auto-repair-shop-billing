package br.com.soat.quote

import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID

data class Quote(
    val orderId: UUID,
    val reservationId: UUID,
    val customerName: String,
    val customerEmail: String,
    val lineItems: List<LineItem>,
    val totalAmount: BigDecimal,
    val status: QuoteStatus = QuoteStatus.PENDING_APPROVAL,
    val paymentId: String? = null,
    val preferenceId: String? = null,
    val createdAt: LocalDateTime = now(),
    val modifiedAt: LocalDateTime = now(),
    val version: Int = 0,
) {
    data class LineItem(val name: String, val price: BigDecimal)

    fun approved(preferenceId: String): Quote =
        copy(status = QuoteStatus.APPROVED, preferenceId = preferenceId, modifiedAt = now())

    fun rejected(): Quote = copy(status = QuoteStatus.REJECTED, modifiedAt = now())

    fun paid(paymentId: String): Quote =
        copy(status = QuoteStatus.PAID, paymentId = paymentId, modifiedAt = now())

    fun paymentFailed(): Quote = copy(status = QuoteStatus.PAYMENT_FAILED, modifiedAt = now())

    fun refunded(): Quote = copy(status = QuoteStatus.REFUNDED, modifiedAt = now())
}
