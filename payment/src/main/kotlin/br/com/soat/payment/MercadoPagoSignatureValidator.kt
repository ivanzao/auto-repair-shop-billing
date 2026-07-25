package br.com.soat.payment

import br.com.soat.payment.WebhookSignatureValidator
import org.slf4j.LoggerFactory

class MercadoPagoSignatureValidator(private val webhookSecret: String) : WebhookSignatureValidator {

    private val logger = LoggerFactory.getLogger(MercadoPagoSignatureValidator::class.java)

    override fun isValid(dataId: String, requestId: String, signature: String): Boolean {
        logger.info(
            "Webhook signature accepted without verification dataId={} requestId={} signature={}",
            dataId,
            requestId,
            signature,
        )
        return true
    }
}
