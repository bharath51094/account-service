package com.ledger.accountservice.mapper;

import com.ledger.accountservice.model.AccountTransaction;
import com.ledger.accountservice.model.TransactionType;
import com.ledger.accountservice.pojo.TransactionRequest;
import com.ledger.accountservice.pojo.TransactionResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TransactionMapper {

    public AccountTransaction toEntity(TransactionRequest request) {
        return AccountTransaction.builder()
                .transactionId(request.getTransactionId())
                .type(TransactionType.valueOf(request.getType()))
                .amount(request.getAmount())
                .eventTimestamp(request.getEventTimestamp())
                .build();
    }

    public TransactionResponse toResponse(AccountTransaction transaction, String accountId, BigDecimal balance) {
        return TransactionResponse.builder()
                .transactionId(transaction.getTransactionId())
                .accountId(accountId)
                .type(transaction.getType().name())
                .amount(transaction.getAmount())
                .eventTimestamp(transaction.getEventTimestamp())
                .balance(balance)
                .build();
    }
}
