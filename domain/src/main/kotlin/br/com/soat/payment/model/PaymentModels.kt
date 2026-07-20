package br.com.soat.payment.model

import java.math.BigDecimal
import java.util.UUID

data class PaymentPreference(val preferenceId: String, val initPoint: String)

enum class PaymentState { APPROVED, PENDING, REJECTED }

data class PaymentDetails(
    val paymentId: String,
    val orderId: UUID,
    val amount: BigDecimal,
    val state: PaymentState,
)
