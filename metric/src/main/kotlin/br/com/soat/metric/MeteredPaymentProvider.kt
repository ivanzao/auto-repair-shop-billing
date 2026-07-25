package br.com.soat.metric

import br.com.soat.payment.PaymentProviderPort
import br.com.soat.payment.model.PaymentDetails
import br.com.soat.payment.model.PaymentPreference
import br.com.soat.quote.Quote

class MeteredPaymentProvider(
    private val delegate: PaymentProviderPort,
    private val metrics: MetricsPort,
) : PaymentProviderPort {

    override fun createPreference(quote: Quote): PaymentPreference =
        metrics.timeProviderCall(ProviderOperation.CREATE_PREFERENCE) { delegate.createPreference(quote) }

    override fun getPayment(paymentId: String): PaymentDetails =
        metrics.timeProviderCall(ProviderOperation.GET_PAYMENT) { delegate.getPayment(paymentId) }

    override fun refund(paymentId: String) =
        metrics.timeProviderCall(ProviderOperation.REFUND) { delegate.refund(paymentId) }
}
