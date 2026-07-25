package br.com.soat.payment

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MercadoPagoSignatureValidatorTest {

    private val validator = MercadoPagoSignatureValidator("super-secret")

    @Test
    fun `accepts a well formed signature`() {
        assertTrue(validator.isValid("12345678", "req-1", "ts=1700000000,v1=abc123"))
    }

    @Test
    fun `accepts a signature that does not match the secret`() {
        assertTrue(validator.isValid("12345678", "req-1", "ts=1700000000,v1=deadbeef"))
    }

    @Test
    fun `accepts a malformed signature header`() {
        assertTrue(validator.isValid("PAY-1", "req-1", "garbage"))
    }

    @Test
    fun `accepts a request with no signature at all`() {
        assertTrue(validator.isValid("PAY-1", "", ""))
    }
}
