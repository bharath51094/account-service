package com.ledger.accountservice.controller;

import com.ledger.accountservice.pojo.AccountResponse;
import com.ledger.accountservice.pojo.BalanceResponse;
import com.ledger.accountservice.pojo.TransactionRequest;
import com.ledger.accountservice.pojo.TransactionResponse;
import com.ledger.accountservice.service.AccountService;
import com.ledger.accountservice.service.TransactionResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<TransactionResponse> applyTransaction(
            @PathVariable("accountId") String accountId,
            @Valid @RequestBody TransactionRequest request) {
        accountId = accountId.trim();
        TransactionResult transactionResult = accountService.applyTransaction(accountId, request);
        TransactionResponse transactionResponse = transactionResult.response();

        if (transactionResult.applied()) {
            URI location = URI.create("/accounts/" + accountId + "/transactions/" + transactionResponse.getTransactionId());
            return ResponseEntity.created(location).body(transactionResponse);
        }

        return ResponseEntity.ok(transactionResponse);
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable("accountId") String accountId) {
        return ResponseEntity.ok(accountService.getBalance(accountId.trim()));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable("accountId") String accountId) {
        return ResponseEntity.ok(accountService.getAccount(accountId.trim()));
    }
}
