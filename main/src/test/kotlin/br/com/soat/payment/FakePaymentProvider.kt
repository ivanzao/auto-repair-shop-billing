package br.com.soat.payment

import br.com.soat.payment.model.PaymentDetails
import br.com.soat.payment.model.PaymentPreference
import br.com.soat.payment.model.PaymentState
import br.com.soat.quote.Quote
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

class FakePaymentProvider(private val backUrlBase: String) : PaymentProviderPort {

    private val logger = LoggerFactory.getLogger(FakePaymentProvider::class.java)
    private val amountsByOrderId = ConcurrentHashMap<UUID, BigDecimal>()

    override fun createPreference(quote: Quote): PaymentPreference {
        amountsByOrderId[quote.orderId] = quote.totalAmount
        logger.info("[FAKE] createPreference for order {}", quote.orderId)
        return PaymentPreference(
            preferenceId = "FAKE-PREF-${quote.orderId}",
            initPoint = "$backUrlBase/fake-checkout?order=${quote.orderId}",
        )
    }

    override fun getPayment(paymentId: String): PaymentDetails {
        val orderId = UUID.fromString(paymentId)
        return PaymentDetails(
            paymentId = paymentId,
            orderId = orderId,
            amount = amountsByOrderId[orderId] ?: BigDecimal.ZERO,
            state = PaymentState.APPROVED,
        )
    }

    override fun refund(paymentId: String) {
        logger.info("[FAKE] refund for payment {}", paymentId)
    }
}
