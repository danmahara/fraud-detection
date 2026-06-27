package com.fraud.detection.transaction;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.fraud.detection.transaction.dto.ContextResult;
import com.fraud.detection.transaction.dto.CreateTransactionRequest;
import com.fraud.detection.transaction.dto.TransactionResponse;

@Service
public class TransactionService {

        private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

        private final AccountRepository accountRepository;
        private final TransactionRepository transactionRepository;
        private final UserProfileRepository userProfileRepository;
        private final MlScoringClient mlScoringClient;
        private final ContextScorer contextScorer; // <-- NEW

        private static final DateTimeFormatter ML_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // How much context can push the score up. 0.5 means a maximally suspicious
        // context (score 1.0) adds 0.5 to the ML score.
        private static final double CONTEXT_BOOST = 0.5;

        public TransactionService(AccountRepository accountRepository,
                        TransactionRepository transactionRepository,
                        UserProfileRepository userProfileRepository,
                        MlScoringClient mlScoringClient,
                        ContextScorer contextScorer) { // <-- NEW param
                this.accountRepository = accountRepository;
                this.transactionRepository = transactionRepository;
                this.userProfileRepository = userProfileRepository;
                this.mlScoringClient = mlScoringClient;
                this.contextScorer = contextScorer;
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

                UserProfile profile = userProfileRepository
                                .findByUser_Id(account.getUser().getId())
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.UNPROCESSABLE_ENTITY,
                                                "No cardholder profile on file for scoring"));

                OffsetDateTime txnTime = request.transactionTime() != null
                                ? request.transactionTime()
                                : OffsetDateTime.now();

                Transaction txn = new Transaction();
                txn.setAccount(account);
                txn.setAmount(request.amount());
                txn.setMerchant(request.merchant());
                txn.setMerchantCategory(request.merchantCategory());
                txn.setChannel(request.channel());
                txn.setLocationLat(request.merchLat());
                txn.setLocationLon(request.merchLon());
                txn.setDeviceId(request.deviceId());
                txn.setTransactionTime(txnTime);
                txn.setStatus(TransactionStatus.PENDING);

                // --- Layer 1: ML scoring (unchanged) ---
                MlPredictRequest mlRequest = new MlPredictRequest(
                                txnTime.format(ML_TS),
                                profile.getDob().toString(),
                                request.amount().doubleValue(),
                                request.merchantCategory(),
                                profile.getHomeLat().doubleValue(),
                                profile.getHomeLon().doubleValue(),
                                request.merchLat().doubleValue(),
                                request.merchLon().doubleValue(),
                                profile.getGender(),
                                profile.getCityPop());
                MlPredictResponse ml = mlScoringClient.predict(mlRequest);
                double mlScore = ml.fraudScore();

                // --- Layers 2 & 3: context scoring (NEW) ---
                double distanceKm = haversineKm(
                                profile.getHomeLat().doubleValue(), profile.getHomeLon().doubleValue(),
                                request.merchLat().doubleValue(), request.merchLon().doubleValue());

                // velocity: transactions for this account in the last 5 minutes of REAL time
                long recentCount = transactionRepository.countByAccountIdAndTransactionTimeAfter(
                                account.getId(), OffsetDateTime.now().minusMinutes(5));

                ContextResult context = contextScorer.score(txn, profile, distanceKm, recentCount);

                // --- Blend: context can escalate, not rescue ML's strong catches ---
                double finalScore = Math.min(1.0, mlScore + context.score() * CONTEXT_BOOST);

                // ...and a strong context score also sets a FLOOR, so behavioural red
                // flags can force a step-up even when the ML model is calm. This is how
                // real risk-based step-up auth works: enough behavioural anomalies
                // demand verification regardless of the transaction's intrinsic look.
                if (context.score() >= 0.55) {
                        finalScore = Math.max(finalScore, 0.90); // -> at least ORANGE (step-up)
                } else if (context.score() >= 0.35) {
                        finalScore = Math.max(finalScore, 0.55); // -> at least YELLOW (monitor)
                }

                // Log the breakdown so we can see the layers working (and for the demo).
                log.info(">>> SCORING txn amount={} | ML={} context={} final={} reasons={}",
                                request.amount(), String.format("%.4f", mlScore),
                                String.format("%.4f", context.score()),
                                String.format("%.4f", finalScore), context.reasons());

                // --- Map final score -> risk band (same thresholds as the ML service) ---
                RiskLevel risk = bandFor(finalScore);
                txn.setRiskLevel(risk);
                txn.setIsolationForestScore(ml.isolationForestScore());
                txn.setXgboostProbability(ml.xgboostProbability());
                txn.setFraudScore(finalScore); // store the BLENDED score

                String decision;
                switch (risk) {
                        case GREEN, YELLOW -> {
                                txn.setStatus(TransactionStatus.APPROVED);
                                decision = "APPROVED";
                        }
                        case ORANGE -> {
                                txn.setStatus(TransactionStatus.PENDING); // awaiting OTP
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

        // Maps the blended score to a risk band (mirrors the ML service bands).
        private RiskLevel bandFor(double score) {
                if (score < 0.50)
                        return RiskLevel.GREEN;
                if (score < 0.80)
                        return RiskLevel.YELLOW;
                if (score < 0.95)
                        return RiskLevel.ORANGE;
                return RiskLevel.RED;
        }

        // Great-circle distance in km between two lat/long points.
        private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
                double R = 6371.0;
                double dLat = Math.toRadians(lat2 - lat1);
                double dLon = Math.toRadians(lon2 - lon1);
                double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                                                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
                return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        }
}