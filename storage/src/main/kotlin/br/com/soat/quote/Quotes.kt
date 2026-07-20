package br.com.soat.quote

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object Quotes : Table("quotes") {
    val orderId = uuid("order_id")
    val reservationId = uuid("reservation_id")
    val customerName = varchar("customer_name", 255)
    val customerEmail = varchar("customer_email", 255)
    val lineItems = text("line_items")
    val totalAmount = decimal("total_amount", 12, 2)
    val status = varchar("status", 30)
    val paymentId = varchar("payment_id", 255).nullable()
    val preferenceId = varchar("preference_id", 255).nullable()
    val createdAt = datetime("created_at")
    val modifiedAt = datetime("modified_at")
    val version = integer("version").default(0)

    init {
        PrimaryKey(orderId)
    }
}
