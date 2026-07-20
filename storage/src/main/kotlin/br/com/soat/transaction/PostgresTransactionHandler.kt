package br.com.soat.transaction

import br.com.soat.shared.repository.RepositoryTransactionHandler
import org.jetbrains.exposed.sql.transactions.transaction

class PostgresTransactionHandler : RepositoryTransactionHandler {
    override fun <T> inTransaction(function: () -> T): T = transaction { function() }
}
