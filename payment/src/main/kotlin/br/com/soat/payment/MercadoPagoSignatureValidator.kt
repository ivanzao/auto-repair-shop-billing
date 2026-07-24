package br.com.soat.payment

import br.com.soat.payment.WebhookSignatureValidator
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MercadoPagoSignatureValidator(private val webhookSecret: String) : WebhookSignatureValidator {

    override fun isValid(dataId: String, requestId: String, signature: String): Boolean {
        val parts = signature.split(",").mapNotNull {
            val kv = it.trim().split("=", limit = 2)
            if (kv.size == 2) kv[0].trim() to kv[1].trim() else null
        }.toMap()

        val ts = parts["ts"] ?: return false
        val v1 = parts["v1"] ?: return false

        val manifest = "id:${dataId.lowercase()};request-id:$requestId;ts:$ts;"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(webhookSecret.toByteArray(), "HmacSHA256"))
        val computed = mac.doFinal(manifest.toByteArray()).joinToString("") { "%02x".format(it) }
        return computed == v1
    }
}
