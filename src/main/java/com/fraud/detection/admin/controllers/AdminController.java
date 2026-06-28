package com.fraud.detection.admin.controllers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fraud.detection.admin.dto.AdminTransactionView;
import com.fraud.detection.admin.dto.AdminUserDetailView;
import com.fraud.detection.admin.dto.AdminUserView;
import com.fraud.detection.admin.dto.PagedAdminTransactions;
import com.fraud.detection.admin.dto.StatsResponse;
import com.fraud.detection.admin.dto.StatsResponse.CountItem;
import com.fraud.detection.entity.Transaction;
import com.fraud.detection.entity.User;
import com.fraud.detection.entity.UserProfile;
import com.fraud.detection.entity.enums.RiskLevel;
import com.fraud.detection.repository.AccountRepository;
import com.fraud.detection.repository.UserProfileRepository;
import com.fraud.detection.repository.UserRepository;
import com.fraud.detection.transaction.TransactionRepository;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')") // every method here is admin-only
public class AdminController {

        private final TransactionRepository transactionRepository;
        private final UserRepository userRepository;
        private final UserProfileRepository userProfileRepository;
        private final AccountRepository accountRepository;

        public AdminController(TransactionRepository transactionRepository,
                        UserRepository userRepository,
                        UserProfileRepository userProfileRepository,
                        AccountRepository accountRepository) {
                this.transactionRepository = transactionRepository;
                this.userRepository = userRepository;
                this.userProfileRepository = userProfileRepository;
                this.accountRepository = accountRepository;
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

        // GET /api/admin/transactions/page?page=0&size=20 -> paginated all
        // transactions.
        @GetMapping("/transactions/page")
        public PagedAdminTransactions transactionsPage(
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size) {

                Page<AdminTransactionView> result = transactionRepository
                                .findAllPaged(PageRequest.of(page, size))
                                .map(t -> new AdminTransactionView(
                                                t.getId(),
                                                t.getAccount().getUser().getEmail(),
                                                t.getAmount(),
                                                t.getMerchant(),
                                                t.getMerchantCategory(),
                                                t.getChannel() == null ? null : t.getChannel().name(),
                                                t.getStatus().name(),
                                                t.getRiskLevel() == null ? null : t.getRiskLevel().name(),
                                                t.getFraudScore(),
                                                t.getFlagReasons(),
                                                t.getTransactionTime()));

                return new PagedAdminTransactions(
                                result.getContent(), result.getNumber(), result.getSize(),
                                result.getTotalElements(), result.getTotalPages());
        }

        @GetMapping("/users")
        public List<AdminUserView> listUsers() {
                return userRepository.findAllByOrderByCreatedAtDesc().stream()
                                .map(u -> new AdminUserView(
                                                u.getId(), u.getName(), u.getEmail(), u.getPhone(),
                                                u.getRole().name(),
                                                transactionRepository.countByUser(u.getId()),
                                                u.getCreatedAt()))
                                .toList();
        }

        // GET /api/admin/users/{id} -> one user's full detail.
        @GetMapping("/users/{id}")
        public AdminUserDetailView getUser(@PathVariable Long id) {
                User u = userRepository.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "User not found"));

                // Profile (may not exist)
                UserProfile p = userProfileRepository.findByUser_Id(id).orElse(null);
                boolean complete = p != null
                                && p.getHomeLat() != null && p.getHomeLon() != null
                                && p.getDob() != null && p.getGender() != null && p.getCityPop() != null;

                // Accounts
                var accounts = accountRepository.findByUserId(id).stream()
                                .map(a -> new AdminUserDetailView.AccountSummary(
                                                a.getId(), a.getAccountNumber(), a.getBalance(), a.getStatus().name()))
                                .toList();

                // Recent transactions (reuse the user-scoped fetch-join query)
                var recent = transactionRepository
                                .findRecentByUser(id, PageRequest.of(0, 10)).stream()
                                .map(t -> new AdminTransactionView(
                                                t.getId(), u.getEmail(), t.getAmount(), t.getMerchant(),
                                                t.getMerchantCategory(),
                                                t.getChannel() == null ? null : t.getChannel().name(),
                                                t.getStatus().name(),
                                                t.getRiskLevel() == null ? null : t.getRiskLevel().name(),
                                                t.getFraudScore(), t.getFlagReasons(), t.getTransactionTime()))
                                .toList();

                return new AdminUserDetailView(
                                u.getId(), u.getName(), u.getEmail(), u.getPhone(),
                                u.getRole().name(), u.getCreatedAt(),
                                p == null ? null : p.getHomeLat(),
                                p == null ? null : p.getHomeLon(),
                                p == null ? null : p.getDob(),
                                p == null ? null : p.getGender(),
                                complete,
                                accounts, recent);
        }
}
