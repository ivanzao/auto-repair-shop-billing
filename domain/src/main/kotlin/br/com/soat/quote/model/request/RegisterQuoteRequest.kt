package br.com.soat.quote.model.request

import java.math.BigDecimal
import java.util.UUID

data class RegisterQuoteRequest(
    val orderId: UUID,
    val reservationId: UUID,
    val customerName: String,
    val customerEmail: String,
    val services: List<ServiceLine>,
    val supplies: List<SupplyLine>,
    val totalAmount: BigDecimal,
) {
    data class ServiceLine(val name: String, val price: BigDecimal)
    data class SupplyLine(val name: String, val quantity: Int, val unitPrice: BigDecimal)
}
