package com.fraud.detection.transaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fraud.detection.entity.Account;
import com.fraud.detection.entity.Transaction;
import com.fraud.detection.entity.enums.RiskLevel;
import com.fraud.detection.entity.enums.TransactionStatus;
import com.fraud.detection.repository.AccountRepository;
import com.fraud.detection.transaction.dto.CreateTransactionRequest;
import com.fraud.detection.transaction.dto.TransactionResponse;

@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(AccountRepository accountRepository,
            TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransactionResponse create(String userEmail, CreateTransactionRequest request) {

        // 1. Find the account being charged.
        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found"));

        // 2. Never trust the body alone: the account must belong to the caller.
        if (!account.getUser().getEmail().equals(userEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "This account does not belong to you");
        }

        // 3. Build the transaction record.
        Transaction txn = new Transaction();
        txn.setAccount(account);
        txn.setAmount(request.amount());
        txn.setMerchant(request.merchant());
        txn.setMerchantCategory(request.merchantCategory());
        txn.setChannel(request.channel());
        txn.setLocationLat(request.locationLat());
        txn.setLocationLon(request.locationLon());
        txn.setDeviceId(request.deviceId());
        txn.setTransactionTime(OffsetDateTime.now());
        txn.setStatus(TransactionStatus.PENDING);

        // 4. TEMPORARY placeholder scoring.
        // The real GREEN/YELLOW/ORANGE/RED decision will come from the
        // Kafka -> FastAPI (Isolation Forest + XGBoost + context) pipeline
        // we build next. For now, a trivial amount rule so we can see the
        // decision flow working end to end.
        RiskLevel risk = placeholderRisk(request.amount());
        txn.setRiskLevel(risk);

        String decision;
        switch (risk) {
            case GREEN, YELLOW -> {
                txn.setStatus(TransactionStatus.APPROVED);
                decision = "APPROVED";
            }
            case ORANGE -> {
                txn.setStatus(TransactionStatus.PENDING); // awaiting OTP step-up
                decision = "OTP_REQUIRED";
            }
            case RED -> {
                txn.setStatus(TransactionStatus.BLOCKED);
                decision = "DECLINED";
            }
            default -> decision = "APPROVED";
        }

        Transaction saved = transactionRepository.save(txn);

        return new TransactionResponse(
                saved.getId(),
                saved.getStatus().name(),
                saved.getRiskLevel().name(),
                decision);
    }

    // TEMPORARY stand-in for the ML pipeline. Replace this whole method later.
    private RiskLevel placeholderRisk(BigDecimal amount) {
        if (amount.compareTo(new BigDecimal("10000")) < 0)
            return RiskLevel.GREEN;
        if (amount.compareTo(new BigDecimal("50000")) < 0)
            return RiskLevel.ORANGE;
        return RiskLevel.RED;
    }
}