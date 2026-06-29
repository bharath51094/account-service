package com.ledger.accountservice.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {

    private String transactionId;
    private String accountId;
    private String type;
    private BigDecimal amount;
    private OffsetDateTime eventTimestamp;
    private BigDecimal balance;
}
