package br.com.soat.payment

interface WebhookSignatureValidator {
    fun isValid(dataId: String, requestId: String, signature: String): Boolean
}
