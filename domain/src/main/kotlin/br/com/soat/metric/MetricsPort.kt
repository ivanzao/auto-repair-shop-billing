package br.com.soat.metric

import br.com.soat.payment.model.PaymentState
import br.com.soat.quote.QuoteStatus

enum class WebhookResult { ACCEPTED, INVALID_SIGNATURE, IGNORED_TYPE }

enum class ProviderOperation { CREATE_PREFERENCE, GET_PAYMENT, REFUND }

enum class CallOutcome { SUCCESS, ERROR }

interface MetricsPort {
    fun quoteCreated()
    fun quoteStatusChanged(status: QuoteStatus)
    fun paymentSettled(state: PaymentState)
    fun webhookReceived(result: WebhookResult)
    fun <T> timeProviderCall(operation: ProviderOperation, block: () -> T): T
}
