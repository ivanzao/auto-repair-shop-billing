package br.com.soat.metric

import br.com.soat.payment.model.PaymentState
import br.com.soat.quote.QuoteStatus

class RecordingMetrics : MetricsPort {
    val payments = mutableListOf<PaymentState>()
    val webhooks = mutableListOf<WebhookResult>()
    val quoteStatuses = mutableListOf<QuoteStatus>()
    var quotesCreated = 0

    override fun quoteCreated() { quotesCreated++ }
    override fun quoteStatusChanged(status: QuoteStatus) { quoteStatuses += status }
    override fun paymentSettled(state: PaymentState) { payments += state }
    override fun webhookReceived(result: WebhookResult) { webhooks += result }
    override fun <T> timeProviderCall(operation: ProviderOperation, block: () -> T): T = block()
}
