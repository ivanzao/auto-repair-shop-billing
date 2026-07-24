package br.com.soat.payment

class PaymentProviderException(
    val statusCode: Int,
    val responseBody: String,
) : RuntimeException("Mercado Pago request failed with status $statusCode: $responseBody")
