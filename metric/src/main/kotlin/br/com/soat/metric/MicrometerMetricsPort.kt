package br.com.soat.metric

import br.com.soat.payment.model.PaymentState
import br.com.soat.quote.QuoteStatus
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.ConcurrentHashMap

class MicrometerMetricsPort(private val registry: MeterRegistry) : MetricsPort {

    private val quotesIssued: Counter = Counter.builder("billing_quotes_issued_total")
        .description("Total de orçamentos emitidos")
        .register(registry)

    private val quotesByStatus: MutableMap<QuoteStatus, Counter> = ConcurrentHashMap()
    private val paymentsByState: MutableMap<PaymentState, Counter> = ConcurrentHashMap()
    private val webhooksByResult: MutableMap<WebhookResult, Counter> = ConcurrentHashMap()
    private val providerTimers: MutableMap<Pair<ProviderOperation, CallOutcome>, Timer> = ConcurrentHashMap()

    override fun quoteCreated() = quotesIssued.increment()

    override fun quoteStatusChanged(status: QuoteStatus) {
        quotesByStatus.computeIfAbsent(status) {
            Counter.builder("billing_quotes_by_status_total")
                .description("Total de transições de orçamento para cada status")
                .tag("status", it.name)
                .register(registry)
        }.increment()
    }

    override fun paymentSettled(state: PaymentState) {
        paymentsByState.computeIfAbsent(state) {
            Counter.builder("billing_payments_total")
                .description("Total de notificações de pagamento por estado final")
                .tag("state", it.name)
                .register(registry)
        }.increment()
    }

    override fun webhookReceived(result: WebhookResult) {
        webhooksByResult.computeIfAbsent(result) {
            Counter.builder("billing_webhooks_total")
                .description("Total de webhooks recebidos por desfecho")
                .tag("result", it.name)
                .register(registry)
        }.increment()
    }

    override fun <T> timeProviderCall(operation: ProviderOperation, block: () -> T): T {
        val sample = Timer.start(registry)
        var outcome = CallOutcome.SUCCESS
        try {
            return block()
        } catch (e: Throwable) {
            outcome = CallOutcome.ERROR
            throw e
        } finally {
            sample.stop(timer(operation, outcome))
        }
    }

    private fun timer(operation: ProviderOperation, outcome: CallOutcome): Timer =
        providerTimers.computeIfAbsent(operation to outcome) { (op, out) ->
            Timer.builder("billing_provider_requests")
                .description("Latência das chamadas ao provider de pagamento")
                .tag("dependency", "mercadopago")
                .tag("operation", op.name)
                .tag("outcome", out.name)
                .publishPercentileHistogram()
                .register(registry)
        }
}
