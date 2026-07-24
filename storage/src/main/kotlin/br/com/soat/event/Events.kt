package br.com.soat.event

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object Events : Table("events") {
    val id = uuid("id")
    val createdAt = datetime("created_at")
    val modifiedAt = datetime("modified_at")
    val version = integer("version").default(0)

    val type = varchar("type", 255)
    val payload = text("payload")
    val traceparent = varchar("traceparent", 64).nullable()

    init {
        PrimaryKey(id)
    }
}
