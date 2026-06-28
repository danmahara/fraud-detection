package com.fraud.detection.transaction;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.fraud.detection.entity.Transaction;
import com.fraud.detection.entity.enums.RiskLevel;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

  // @Query("""
  // SELECT t FROM Transaction t
  // JOIN FETCH t.account a
  // JOIN FETCH a.user
  // ORDER BY t.transactionTime DESC
  // """)
  // List<Transaction> findRecentWithAccount(Pageable pageable);

  @Query("""
      SELECT t FROM Transaction t
      JOIN FETCH t.account a
      JOIN FETCH a.user
      ORDER BY t.createdAt DESC
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

  // Velocity check: how many transactions this account made since a cutoff time.
  long countByAccountIdAndTransactionTimeAfter(Long accountId, OffsetDateTime after);

  // ############ For admin dashboard stats ############

  // Count rows at one specific risk level (whole table). Distinct name to avoid
  // clashing with the grouped countByRiskLevel() used for the chart.
  long countByRiskLevelEquals(RiskLevel riskLevel);

  // Count flagged (ORANGE or RED) — whole table.
  @Query("""
      SELECT COUNT(t) FROM Transaction t
      WHERE t.riskLevel IN (com.fraud.detection.entity.enums.RiskLevel.ORANGE,
                            com.fraud.detection.entity.enums.RiskLevel.RED)
      """)
  long countAllFlagged();

  // --- "Today" variants: count rows created since a cutoff (start of today) ---
  long countByCreatedAtAfter(OffsetDateTime cutoff);

  long countByRiskLevelAndCreatedAtAfter(RiskLevel riskLevel, OffsetDateTime cutoff);

  @Query("""
      SELECT COUNT(t) FROM Transaction t
      WHERE t.createdAt >= :cutoff
        AND t.riskLevel IN (com.fraud.detection.entity.enums.RiskLevel.ORANGE,
                            com.fraud.detection.entity.enums.RiskLevel.RED)
      """)
  long countFlaggedSince(@org.springframework.data.repository.query.Param("cutoff") OffsetDateTime cutoff);

  // Paginated ALL transactions for the admin console (with account+user).
  @Query(value = """
      SELECT t FROM Transaction t
      JOIN FETCH t.account a
      JOIN FETCH a.user u
      ORDER BY t.createdAt DESC
      """, countQuery = "SELECT COUNT(t) FROM Transaction t")
  Page<Transaction> findAllPaged(Pageable pageable);

  // --- User-scoped (customer dashboard): filter to one user's account(s) ---

  // This user's recent transactions (JOIN FETCH account+user to avoid lazy
  // errors).
  @Query("""
      SELECT t FROM Transaction t
      JOIN FETCH t.account a
      JOIN FETCH a.user u
      WHERE u.id = :userId
      ORDER BY t.createdAt DESC
      """)
  List<Transaction> findRecentByUser(@org.springframework.data.repository.query.Param("userId") Long userId,
      Pageable pageable);

  // Total transactions for this user.
  @Query("SELECT COUNT(t) FROM Transaction t WHERE t.account.user.id = :userId")
  long countByUser(@org.springframework.data.repository.query.Param("userId") Long userId);

  // Count this user's transactions at a specific risk level.
  @Query("SELECT COUNT(t) FROM Transaction t WHERE t.account.user.id = :userId AND t.riskLevel = :riskLevel")
  long countByUserAndRiskLevel(@org.springframework.data.repository.query.Param("userId") Long userId,
      @org.springframework.data.repository.query.Param("riskLevel") RiskLevel riskLevel);

  // Paginated user transactions. Spring runs the count query automatically.
  @Query(value = """
      SELECT t FROM Transaction t
      JOIN FETCH t.account a
      JOIN FETCH a.user u
      WHERE u.id = :userId
      ORDER BY t.createdAt DESC
      """, countQuery = """
      SELECT COUNT(t) FROM Transaction t
      WHERE t.account.user.id = :userId
      """)
  Page<Transaction> findPageByUser(@org.springframework.data.repository.query.Param("userId") Long userId,
      Pageable pageable);

}
