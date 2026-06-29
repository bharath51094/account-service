package com.ledger.accountservice.service;

import com.ledger.accountservice.pojo.TransactionResponse;

/**
 * Result of applying a transaction.
 *
 * @param response the resulting transaction + account balance
 * @param applied  {@code true} if the transaction was newly applied, {@code false} if a transaction
 *                 with the same transactionId already existed (idempotent replay)
 */
public record TransactionResult(TransactionResponse response, boolean applied) {
}
