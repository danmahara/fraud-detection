package com.fraud.detection.admin.controllers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fraud.detection.admin.dto.AdminTransactionView;
import com.fraud.detection.admin.dto.StatsResponse;
import com.fraud.detection.admin.dto.StatsResponse.CountItem;
import com.fraud.detection.entity.Transaction;
import com.fraud.detection.entity.enums.RiskLevel;
import com.fraud.detection.transaction.TransactionRepository;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')") // every method here is admin-only
public class AdminController {

        private final TransactionRepository transactionRepository;

        public AdminController(TransactionRepository transactionRepository) {
                this.transactionRepository = transactionRepository;
        }

        // GET /api/admin/transactions?limit=50 -> most recent transactions, newest
        // first.
        @GetMapping("/transactions")
        public List<AdminTransactionView> recentTransactions(
                        @RequestParam(defaultValue = "50") int limit) {

                List<Transaction> txns = transactionRepository.findRecentWithAccount(PageRequest.of(0, limit));

                return txns.stream()
                                .map(t -> new AdminTransactionView(
                                                t.getId(),
                                                t.getAccount().getUser().getEmail(),
                                                t.getAmount(),
                                                t.getMerchant(),
                                                t.getMerchantCategory(),
                                                t.getChannel() != null ? t.getChannel().name() : null,
                                                t.getStatus().name(),
                                                t.getRiskLevel() != null ? t.getRiskLevel().name() : null,
                                                t.getFraudScore(),
                                                t.getFlagReasons(),
                                                t.getTransactionTime()))
                                .toList();
        }

        @GetMapping("/stats")
        public StatsResponse stats() {
                long total = transactionRepository.count();
                long flagged = transactionRepository.countAllFlagged();
                long blocked = transactionRepository.countByRiskLevelEquals(RiskLevel.RED);

                // Start of today (UTC). Adjust the zone if you want local midnight.
                OffsetDateTime startOfToday = OffsetDateTime.now(ZoneOffset.UTC)
                                .toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);

                long todayTotal = transactionRepository.countByCreatedAtAfter(startOfToday);
                long todayFlagged = transactionRepository.countFlaggedSince(startOfToday);
                long todayBlocked = transactionRepository
                                .countByRiskLevelAndCreatedAtAfter(RiskLevel.RED, startOfToday);

                List<CountItem> byRisk = transactionRepository.countByRiskLevel().stream()
                                .map(row -> new CountItem(
                                                row[0] != null ? ((RiskLevel) row[0]).name() : "UNKNOWN",
                                                (long) row[1]))
                                .toList();

                List<CountItem> byCategory = transactionRepository.countByCategory().stream()
                                .map(row -> new CountItem(
                                                row[0] != null ? (String) row[0] : "unknown",
                                                (long) row[1]))
                                .toList();

                double flaggedRate = total > 0 ? (double) flagged / total : 0.0;

                return new StatsResponse(total, flagged, blocked,
                                todayTotal, todayFlagged, todayBlocked,
                                flaggedRate, byRisk, byCategory);
        }

}
