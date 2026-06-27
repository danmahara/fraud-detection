package com.fraud.detection.transaction;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.fraud.detection.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("""
            SELECT t FROM Transaction t
            JOIN FETCH t.account a
            JOIN FETCH a.user
            ORDER BY t.transactionTime DESC
            """)
    List<Transaction> findRecentWithAccount(Pageable pageable);

}
