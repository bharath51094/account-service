package com.ledger.accountservice.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountResponse {

    private String accountId;
    private BigDecimal balance;
    private String currency;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<TransactionSummary> transactions;
}
