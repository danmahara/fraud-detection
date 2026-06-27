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

    // Count of transactions grouped by risk level -> [["GREEN", 42], ["RED", 5],
    // ...]
    @Query("""
            SELECT t.riskLevel, COUNT(t)
            FROM Transaction t
            GROUP BY t.riskLevel
            """)
    List<Object[]> countByRiskLevel();

    // Count grouped by merchant category -> [["grocery_pos", 30], ...]
    @Query("""
            SELECT t.merchantCategory, COUNT(t)
            FROM Transaction t
            GROUP BY t.merchantCategory
            ORDER BY COUNT(t) DESC
            """)
    List<Object[]> countByCategory();

    // How many transactions are flagged (ORANGE/RED) vs not.
    @Query("""
            SELECT COUNT(t)
            FROM Transaction t
            WHERE t.riskLevel IN (com.fraud.detection.entity.enums.RiskLevel.ORANGE,
                                  com.fraud.detection.entity.enums.RiskLevel.RED)
            """)
    long countFlagged();

    long count(); // inherited from JpaRepository, listed here for clarity
}
