package com.fraud.detection.admin.controllers;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fraud.detection.admin.dto.AdminTransactionView;
import com.fraud.detection.entity.Transaction;
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
                        t.getTransactionTime()))
                .toList();
    }
}
