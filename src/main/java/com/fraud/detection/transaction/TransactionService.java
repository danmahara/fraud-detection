package com.fraud.detection.transaction;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fraud.detection.entity.Account;
import com.fraud.detection.entity.Transaction;
import com.fraud.detection.entity.UserProfile;
import com.fraud.detection.entity.enums.RiskLevel;
import com.fraud.detection.entity.enums.TransactionStatus;
import com.fraud.detection.ml.MlScoringClient;
import com.fraud.detection.ml.dto.MlPredictRequest;
import com.fraud.detection.ml.dto.MlPredictResponse;
import com.fraud.detection.repository.AccountRepository;
import com.fraud.detection.repository.UserProfileRepository;
import com.fraud.detection.transaction.dto.CreateTransactionRequest;
import com.fraud.detection.transaction.dto.TransactionResponse;

@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserProfileRepository userProfileRepository;
    private final MlScoringClient mlScoringClient;

    // The ML service expects "yyyy-MM-dd HH:mm:ss" (matches the training data).
    private static final DateTimeFormatter ML_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public TransactionService(AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            UserProfileRepository userProfileRepository,
            MlScoringClient mlScoringClient) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.userProfileRepository = userProfileRepository;
        this.mlScoringClient = mlScoringClient;
    }

    @Transactional
    public TransactionResponse create(String userEmail, CreateTransactionRequest request) {

        // 1. Find the account and confirm it belongs to the caller.
        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found"));

        if (!account.getUser().getEmail().equals(userEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "This account does not belong to you");
        }

        // 2. Load the cardholder's profile (home location, dob, gender, city_pop).
        // The model needs these; the bank already knows them.
        UserProfile profile = userProfileRepository
                .findByUser_Id(account.getUser().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "No cardholder profile on file for scoring"));

        // 3. Resolve the transaction time: use the supplied one, else server now().
        OffsetDateTime txnTime = request.transactionTime() != null
                ? request.transactionTime()
                : OffsetDateTime.now();

        // 4. Build the transaction record.
        Transaction txn = new Transaction();
        txn.setAccount(account);
        txn.setAmount(request.amount());
        txn.setMerchant(request.merchant());
        txn.setMerchantCategory(request.merchantCategory());
        txn.setChannel(request.channel());
        txn.setLocationLat(request.merchLat()); // store where the purchase happened
        txn.setLocationLon(request.merchLon());
        txn.setDeviceId(request.deviceId());
        txn.setTransactionTime(txnTime);
        txn.setStatus(TransactionStatus.PENDING);

        // 5. Merge profile + transaction into the ML request and score it.
        MlPredictRequest mlRequest = new MlPredictRequest(
                txnTime.format(ML_TS), // trans_date_trans_time
                profile.getDob().toString(), // dob (yyyy-MM-dd)
                request.amount().doubleValue(), // amt
                request.merchantCategory(), // category
                profile.getHomeLat().doubleValue(), // lat (cardholder home)
                profile.getHomeLon().doubleValue(), // long (cardholder home)
                request.merchLat().doubleValue(), // merch_lat
                request.merchLon().doubleValue(), // merch_long
                profile.getGender(), // gender
                profile.getCityPop()); // city_pop

        MlPredictResponse ml = mlScoringClient.predict(mlRequest);

        // 6. Persist what the ML service returned.
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