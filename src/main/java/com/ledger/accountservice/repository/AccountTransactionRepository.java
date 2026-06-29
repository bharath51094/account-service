package com.ledger.accountservice.repository;

import com.ledger.accountservice.model.AccountTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {

    Optional<AccountTransaction> findByTransactionId(String transactionId);

    List<AccountTransaction> findTop10ByAccount_AccountIdOrderByEventTimestampDesc(String accountId);
}
