package br.com.soat

import br.com.soat.config.Config
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

fun connectToDatabase(config: Config): DataSource {
    val datasource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = config.getString("database.url")
        driverClassName = config.getString("database.driverClassName")
        username = config.getString("database.username")
        password = config.getString("database.password")
        maximumPoolSize = config.getInt("database.maximumPoolSize")
        isReadOnly = false
        transactionIsolation = "TRANSACTION_SERIALIZABLE"
    })

    Database.connect(datasource)

    Flyway.configure()
        .dataSource(datasource)
        .locations("classpath:db/migration")
        .load()
        .migrate()

    return datasource
}
