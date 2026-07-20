package br.com.soat.payment

import br.com.soat.payment.model.PaymentDetails
import br.com.soat.payment.model.PaymentPreference
import br.com.soat.quote.Quote

interface PaymentProviderPort {
    fun createPreference(quote: Quote): PaymentPreference
    fun getPayment(paymentId: String): PaymentDetails
    fun refund(paymentId: String)
}
