package com.ledger.accountservice.service;

import com.ledger.accountservice.exception.AccountNotFoundException;
import com.ledger.accountservice.exception.CurrencyMismatchException;
import com.ledger.accountservice.mapper.AccountMapper;
import com.ledger.accountservice.mapper.TransactionMapper;
import com.ledger.accountservice.model.Account;
import com.ledger.accountservice.model.AccountTransaction;
import com.ledger.accountservice.model.TransactionType;
import com.ledger.accountservice.pojo.AccountResponse;
import com.ledger.accountservice.pojo.BalanceResponse;
import com.ledger.accountservice.pojo.TransactionRequest;
import com.ledger.accountservice.pojo.TransactionResponse;
import com.ledger.accountservice.repository.AccountRepository;
import com.ledger.accountservice.repository.AccountTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountTransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final AccountMapper accountMapper;

    @Transactional
    public TransactionResult applyTransaction(String accountId, TransactionRequest request) {
        // Idempotency: a replayed transactionId must not be applied twice (would corrupt the balance).
        Optional<AccountTransaction> existingAccountTransaction = transactionRepository.findByTransactionId(request.getTransactionId());
        if (existingAccountTransaction.isPresent()) {
            Account account = existingAccountTransaction.get().getAccount();
            TransactionResponse transactionResponse =
                    transactionMapper.toResponse(existingAccountTransaction.get(), accountId, account.getBalance());
            return new TransactionResult(transactionResponse, false);
        }

        try {
            Account account = accountRepository.findByAccountIdForUpdate(accountId)
                    .orElseGet(() -> createAccount(accountId, request.getCurrency()));
            validateCurrency(account, request.getCurrency());

            // Incremental running balance: previous balance +/- this request's amount.
            // The account row is held under a pessimistic write lock, so concurrent
            // transactions for the same account serialize and cannot lost-update the balance.
            BigDecimal newBalance = computeNewBalance(account.getBalance(), request);
            account.setBalance(newBalance);

            AccountTransaction transaction = transactionMapper.toEntity(request);
            account.addTransaction(transaction);
            accountRepository.save(account); // cascade persists the new transaction + updated balance

            TransactionResponse transactionResponse = transactionMapper.toResponse(transaction, accountId, newBalance);
            return new TransactionResult(transactionResponse, true);
        } catch (DataIntegrityViolationException e) {
            // Concurrent submission won the race on the unique transactionId; return the persisted state.
            AccountTransaction raceWinner = transactionRepository.findByTransactionId(request.getTransactionId())
                    .orElseThrow(() -> e);
            TransactionResponse transactionResponse =
                    transactionMapper.toResponse(raceWinner, accountId, raceWinner.getAccount().getBalance());
            return new TransactionResult(transactionResponse, false);
        }
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountId) {
        Account account = accountRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        return accountMapper.toBalanceResponse(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String accountId) {
        Account account = accountRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        List<AccountTransaction> transactions =
                transactionRepository.findTop10ByAccount_AccountIdOrderByEventTimestampDesc(accountId);
        return accountMapper.toAccountResponse(account, transactions);
    }

    private Account createAccount(String accountId, String currency) {
        return Account.builder()
                .accountId(accountId)
                .balance(BigDecimal.ZERO)
                .currency(currency)
                .build();
    }

    private void validateCurrency(Account account, String requestCurrency) {
        if (!account.getCurrency().equals(requestCurrency)) {
            throw new CurrencyMismatchException(
                    "Currency mismatch: account " + account.getAccountId() + " uses " + account.getCurrency()
                            + ", but transaction is in " + requestCurrency);
        }
    }

    private BigDecimal computeNewBalance(BigDecimal currentBalance, TransactionRequest request) {
        BigDecimal amount = request.getAmount();
        return TransactionType.valueOf(request.getType()) == TransactionType.CREDIT
                ? currentBalance.add(amount)
                : currentBalance.subtract(amount);
    }
}
