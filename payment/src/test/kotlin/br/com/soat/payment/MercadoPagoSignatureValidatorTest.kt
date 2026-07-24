package br.com.soat.payment

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MercadoPagoSignatureValidatorTest {

    private val secret = "super-secret"
    private val validator = MercadoPagoSignatureValidator(secret)

    private fun hmac(manifest: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(manifest.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `accepts a signature matching the manifest`() {
        val dataId = "12345678"
        val requestId = "req-1"
        val ts = "1700000000"
        val v1 = hmac("id:$dataId;request-id:$requestId;ts:$ts;")

        assertTrue(validator.isValid(dataId, requestId, "ts=$ts,v1=$v1"))
    }

    @Test
    fun `lowercases an alphanumeric data id before building the manifest`() {
        // Mercado Pago requires alphanumeric data.id to be lowercased in the manifest.
        val dataId = "ORD01JQ4S4KY8HWQ6NA5PXB65B3D3"
        val requestId = "req-1"
        val ts = "1700000000"
        val v1 = hmac("id:${dataId.lowercase()};request-id:$requestId;ts:$ts;")

        assertTrue(validator.isValid(dataId, requestId, "ts=$ts,v1=$v1"))
    }

    @Test
    fun `rejects a tampered signature`() {
        val dataId = "PAY-1"
        val requestId = "req-1"
        val ts = "1700000000"
        val v1 = hmac("id:$dataId;request-id:$requestId;ts:$ts;")
        val tampered = v1.replaceFirst(v1.first(), if (v1.first() == 'a') 'b' else 'a')

        assertFalse(validator.isValid(dataId, requestId, "ts=$ts,v1=$tampered"))
    }

    @Test
    fun `rejects a malformed header`() {
        assertFalse(validator.isValid("PAY-1", "req-1", "garbage"))
    }
}
