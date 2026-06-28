package com.fraud.detection.account;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fraud.detection.account.dto.AccountView;
import com.fraud.detection.entity.User;
import com.fraud.detection.entity.enums.RiskLevel;
import com.fraud.detection.me.dto.MyStatsView;
import com.fraud.detection.me.dto.MyTransactionView;
import com.fraud.detection.me.dto.PagedTransactions;
import com.fraud.detection.repository.AccountRepository;
import com.fraud.detection.repository.UserRepository;
import com.fraud.detection.transaction.TransactionRepository;

@RestController
@RequestMapping("/api/me")
public class AccountController {

        private final UserRepository userRepository;
        private final AccountRepository accountRepository;
        private final TransactionRepository transactionRepository;

        public AccountController(UserRepository userRepository,
                        AccountRepository accountRepository,
                        TransactionRepository transactionRepository) {
                this.userRepository = userRepository;
                this.accountRepository = accountRepository;
                this.transactionRepository = transactionRepository;
        }

        // GET /api/me/accounts -> the logged-in user's own accounts.
        @GetMapping("/accounts")
        public List<AccountView> myAccounts(Authentication authentication) {
                User user = userRepository.findByEmail(authentication.getName())
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "User not found"));

                return accountRepository.findByUserId(user.getId()).stream()
                                .map(a -> new AccountView(
                                                a.getId(),
                                                a.getAccountNumber(),
                                                a.getBalance(),
                                                a.getStatus().name()))
                                .toList();
        }

        // GET /api/me/transactions?limit=10 -> this user's recent transactions.
        @GetMapping("/transactions")
        public List<MyTransactionView> myTransactions(
                        @RequestParam(defaultValue = "10") int limit,
                        Authentication authentication) {

                User user = userRepository.findByEmail(authentication.getName())
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "User not found"));

                return transactionRepository
                                .findRecentByUser(user.getId(), PageRequest.of(0, limit))
                                .stream()
                                .map(t -> new MyTransactionView(
                                                t.getId(),
                                                t.getAmount(),
                                                t.getMerchant(),
                                                t.getMerchantCategory(),
                                                t.getChannel() == null ? null : t.getChannel().name(),
                                                t.getStatus().name(),
                                                t.getRiskLevel() == null ? null : t.getRiskLevel().name(),
                                                t.getFlagReasons(),
                                                t.getTransactionTime()))
                                .toList();
        }

        // GET /api/me/stats -> this user's transaction totals.
        @GetMapping("/stats")
        public MyStatsView myStats(Authentication authentication) {
                User user = userRepository.findByEmail(authentication.getName())
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "User not found"));

                Long uid = user.getId();
                long total = transactionRepository.countByUser(uid);
                long green = transactionRepository.countByUserAndRiskLevel(uid, RiskLevel.GREEN);
                long yellow = transactionRepository.countByUserAndRiskLevel(uid, RiskLevel.YELLOW);
                long orange = transactionRepository.countByUserAndRiskLevel(uid, RiskLevel.ORANGE);
                long red = transactionRepository.countByUserAndRiskLevel(uid, RiskLevel.RED);

                return new MyStatsView(
                                total,
                                green + yellow, // approved
                                orange, // flagged / needs verification
                                red); // blocked
        }

        // GET /api/me/transactions/page?page=0&size=10 -> paginated transactions.
        @GetMapping("/transactions/page")
        public PagedTransactions myTransactionsPage(
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        Authentication authentication) {

                User user = userRepository.findByEmail(authentication.getName())
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "User not found"));

                Page<MyTransactionView> result = transactionRepository
                                .findPageByUser(user.getId(), PageRequest.of(page, size))
                                .map(t -> new MyTransactionView(
                                                t.getId(),
                                                t.getAmount(),
                                                t.getMerchant(),
                                                t.getMerchantCategory(),
                                                t.getChannel() == null ? null : t.getChannel().name(),
                                                t.getStatus().name(),
                                                t.getRiskLevel() == null ? null : t.getRiskLevel().name(),
                                                t.getFlagReasons(),
                                                t.getTransactionTime()));

                return new PagedTransactions(
                                result.getContent(),
                                result.getNumber(),
                                result.getSize(),
                                result.getTotalElements(),
                                result.getTotalPages());
        }
}