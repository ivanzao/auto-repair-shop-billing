package br.com.soat.metric

import br.com.soat.payment.PaymentProviderPort
import br.com.soat.payment.model.PaymentDetails
import br.com.soat.payment.model.PaymentPreference
import br.com.soat.payment.model.PaymentState
import br.com.soat.quote.Quote
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.math.BigDecimal
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MeteredPaymentProviderTest {

    private val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val metrics = MicrometerMetricsPort(registry)
    private val orderId = UUID.randomUUID()

    private fun timerCount(operation: ProviderOperation, outcome: CallOutcome): Long =
        registry.get("billing_provider_requests")
            .tag("operation", operation.name)
            .tag("outcome", outcome.name)
            .timer().count()

    private class StubProvider(
        private val onGetPayment: () -> PaymentDetails,
    ) : PaymentProviderPort {
        override fun createPreference(quote: Quote) = PaymentPreference("PREF-1", "http://checkout/1")
        override fun getPayment(paymentId: String) = onGetPayment()
        override fun refund(paymentId: String) = Unit
    }

    @Test
    fun `times a successful getPayment call`() {
        val details = PaymentDetails(
            paymentId = "pay-1",
            orderId = orderId,
            amount = BigDecimal("10.00"),
            state = PaymentState.APPROVED,
        )
        val provider = MeteredPaymentProvider(StubProvider { details }, metrics)

        assertEquals(details, provider.getPayment("pay-1"))
        assertEquals(1L, timerCount(ProviderOperation.GET_PAYMENT, CallOutcome.SUCCESS))
    }

    @Test
    fun `times a failing getPayment call and rethrows`() {
        val provider = MeteredPaymentProvider(
            StubProvider { throw IllegalStateException("provider down") },
            metrics,
        )

        assertThrows(IllegalStateException::class.java) { provider.getPayment("pay-1") }
        assertEquals(1L, timerCount(ProviderOperation.GET_PAYMENT, CallOutcome.ERROR))
    }

    @Test
    fun `times refund under its own operation label`() {
        val provider = MeteredPaymentProvider(StubProvider { error("unused") }, metrics)

        provider.refund("pay-1")

        assertEquals(1L, timerCount(ProviderOperation.REFUND, CallOutcome.SUCCESS))
    }
}
