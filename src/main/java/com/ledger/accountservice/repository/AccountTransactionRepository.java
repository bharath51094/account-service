package com.ledger.accountservice.repository;

import com.ledger.accountservice.model.AccountTransaction;
import com.ledger.accountservice.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {

    Optional<AccountTransaction> findByTransactionId(String transactionId);

    List<AccountTransaction> findByAccount_AccountIdOrderByEventTimestampDesc(String accountId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM AccountTransaction t "
            + "WHERE t.account.accountId = :accountId AND t.type = :type")
    BigDecimal sumByType(@Param("accountId") String accountId, @Param("type") TransactionType type);
}
