package dev.ybdn.ciaocloud.domain.repository

/** Exécute plusieurs écritures de repositories de façon atomique (transaction Room). */
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}
