package com.fraud.detection.transaction;

import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fraud.detection.entity.Account;
import com.fraud.detection.entity.Transaction;
import com.fraud.detection.entity.enums.RiskLevel;
import com.fraud.detection.entity.enums.TransactionStatus;
import com.fraud.detection.ml.MlScoringClient;
import com.fraud.detection.ml.dto.MlPredictRequest;
import com.fraud.detection.ml.dto.MlPredictResponse;
import com.fraud.detection.repository.AccountRepository;
import com.fraud.detection.transaction.dto.CreateTransactionRequest;
import com.fraud.detection.transaction.dto.TransactionResponse;

@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final MlScoringClient mlScoringClient;

    public TransactionService(AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            MlScoringClient mlScoringClient) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.mlScoringClient = mlScoringClient;
    }

    @Transactional
    public TransactionResponse create(String userEmail, CreateTransactionRequest request) {

        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found"));

        if (!account.getUser().getEmail().equals(userEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "This account does not belong to you");
        }

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

        // --- Real scoring: call the FastAPI ML service synchronously ---
        MlPredictRequest mlRequest = new MlPredictRequest(
                request.features(), // V-feature vector (null for manual entry)
                request.amount(),
                request.merchantCategory(),
                request.channel().name(), // Channel enum -> "POS" etc.
                request.locationLat(),
                request.locationLon(),
                request.deviceId());
        MlPredictResponse ml = mlScoringClient.predict(mlRequest);

        // Persist what the ML service returned.
        RiskLevel risk = RiskLevel.valueOf(ml.riskLevel());
        txn.setRiskLevel(risk);
        txn.setIsolationForestScore(ml.isolationForestScore());
        txn.setXgboostProbability(ml.xgboostProbability());
        txn.setFraudScore(ml.fraudScore());

        switch (risk) {
            case GREEN, YELLOW -> txn.setStatus(TransactionStatus.APPROVED);
            case ORANGE -> txn.setStatus(TransactionStatus.PENDING); // awaiting OTP
            case RED -> txn.setStatus(TransactionStatus.BLOCKED);
        }

        Transaction saved = transactionRepository.save(txn);

        return new TransactionResponse(
                saved.getId(),
                saved.getStatus().name(),
                saved.getRiskLevel().name(),
                ml.decision());
    }
}