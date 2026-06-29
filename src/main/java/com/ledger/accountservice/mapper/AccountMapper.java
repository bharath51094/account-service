package com.ledger.accountservice.mapper;

import com.ledger.accountservice.model.Account;
import com.ledger.accountservice.model.AccountTransaction;
import com.ledger.accountservice.pojo.AccountResponse;
import com.ledger.accountservice.pojo.BalanceResponse;
import com.ledger.accountservice.pojo.TransactionSummary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AccountMapper {

    public BalanceResponse toBalanceResponse(Account account) {
        return BalanceResponse.builder()
                .accountId(account.getAccountId())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .build();
    }

    public AccountResponse toAccountResponse(Account account, List<AccountTransaction> transactions) {
        List<TransactionSummary> summaries = transactions.stream()
                .map(this::toSummary)
                .toList();

        return AccountResponse.builder()
                .accountId(account.getAccountId())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .transactions(summaries)
                .build();
    }

    private TransactionSummary toSummary(AccountTransaction transaction) {
        return TransactionSummary.builder()
                .transactionId(transaction.getTransactionId())
                .type(transaction.getType().name())
                .amount(transaction.getAmount())
                .eventTimestamp(transaction.getEventTimestamp())
                .build();
    }
}
