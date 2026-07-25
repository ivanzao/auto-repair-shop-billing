package br.com.soat.metric

import br.com.soat.payment.model.PaymentState
import br.com.soat.quote.QuoteStatus
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MicrometerMetricsPortTest {

    private val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val metrics = MicrometerMetricsPort(registry)

    @Test
    fun `quoteCreated increments the counter`() {
        metrics.quoteCreated()
        metrics.quoteCreated()
        assertEquals(2.0, registry.get("billing_quotes_issued_total").counter().count())
    }

    @Test
    fun `quoteStatusChanged tags by status`() {
        metrics.quoteStatusChanged(QuoteStatus.PAID)
        metrics.quoteStatusChanged(QuoteStatus.PAID)
        metrics.quoteStatusChanged(QuoteStatus.PAYMENT_FAILED)

        assertEquals(2.0, registry.get("billing_quotes_by_status_total").tag("status", "PAID").counter().count())
        assertEquals(1.0, registry.get("billing_quotes_by_status_total").tag("status", "PAYMENT_FAILED").counter().count())
    }

    @Test
    fun `paymentSettled tags by state`() {
        metrics.paymentSettled(PaymentState.APPROVED)
        assertEquals(1.0, registry.get("billing_payments_total").tag("state", "APPROVED").counter().count())
    }

    @Test
    fun `webhookReceived tags by result`() {
        metrics.webhookReceived(WebhookResult.INVALID_SIGNATURE)
        assertEquals(1.0, registry.get("billing_webhooks_total").tag("result", "INVALID_SIGNATURE").counter().count())
    }

    @Test
    fun `timeProviderCall records a success sample and returns the value`() {
        val result = metrics.timeProviderCall(ProviderOperation.CREATE_PREFERENCE) { "PREF-1" }

        assertEquals("PREF-1", result)
        assertEquals(
            1L,
            registry.get("billing_provider_requests")
                .tag("operation", "CREATE_PREFERENCE")
                .tag("outcome", "SUCCESS")
                .timer().count(),
        )
    }

    @Test
    fun `timeProviderCall records an error sample and rethrows`() {
        assertThrows(IllegalStateException::class.java) {
            metrics.timeProviderCall(ProviderOperation.GET_PAYMENT) { throw IllegalStateException("boom") }
        }

        assertEquals(
            1L,
            registry.get("billing_provider_requests")
                .tag("operation", "GET_PAYMENT")
                .tag("outcome", "ERROR")
                .timer().count(),
        )
    }

    @Test
    fun `exports the names the dashboards query, using issued to dodge the reserved _created suffix`() {
        metrics.quoteCreated()
        metrics.quoteStatusChanged(QuoteStatus.PAID)
        metrics.paymentSettled(PaymentState.APPROVED)
        metrics.webhookReceived(WebhookResult.ACCEPTED)
        metrics.timeProviderCall(ProviderOperation.REFUND) { }

        val scraped = registry.scrape()

        listOf(
            "# TYPE billing_quotes_issued_total counter",
            "# TYPE billing_quotes_by_status_total counter",
            "# TYPE billing_payments_total counter",
            "# TYPE billing_webhooks_total counter",
            "# TYPE billing_provider_requests_seconds histogram",
        ).forEach { line ->
            assertTrue(scraped.contains(line), "$line ausente no scrape")
        }
    }
}
