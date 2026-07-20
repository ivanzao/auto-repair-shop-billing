package br.com.soat.shared.repository

interface RepositoryTransactionHandler {
    fun <T> inTransaction(function: () -> T): T
}
