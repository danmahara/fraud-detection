package com.fraud.detection.transaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fraud.detection.entity.Account;
import com.fraud.detection.entity.Merchant;
import com.fraud.detection.entity.Transaction;
import com.fraud.detection.entity.UserProfile;
import com.fraud.detection.entity.enums.RiskLevel;
import com.fraud.detection.entity.enums.TransactionStatus;
import com.fraud.detection.ml.MlScoringClient;
import com.fraud.detection.ml.dto.MlPredictRequest;
import com.fraud.detection.ml.dto.MlPredictResponse;
import com.fraud.detection.repository.AccountRepository;
import com.fraud.detection.repository.MerchantRepository;
import com.fraud.detection.repository.UserProfileRepository;
import com.fraud.detection.transaction.dto.ContextResult;
import com.fraud.detection.transaction.dto.CreateTransactionRequest;
import com.fraud.detection.transaction.dto.TransactionResponse;
import com.fraud.detection.entity.enums.AccountStatus;

@Service
public class TransactionService {

        private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

        private final AccountRepository accountRepository;
        private final TransactionRepository transactionRepository;
        private final UserProfileRepository userProfileRepository;
        private final MlScoringClient mlScoringClient;
        private final ContextScorer contextScorer; // <-- NEW

        private static final DateTimeFormatter ML_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        private final MerchantRepository merchantRepository; // <-- NEW

        // How much context can push the score up. 0.5 means a maximally suspicious
        // context (score 1.0) adds 0.5 to the ML score.
        private static final double CONTEXT_BOOST = 0.5;

        public TransactionService(AccountRepository accountRepository,
                        TransactionRepository transactionRepository,
                        UserProfileRepository userProfileRepository,
                        MlScoringClient mlScoringClient,
                        ContextScorer contextScorer,
                        MerchantRepository merchantRepository) { // <-- NEW param
                this.accountRepository = accountRepository;
                this.transactionRepository = transactionRepository;
                this.userProfileRepository = userProfileRepository;
                this.mlScoringClient = mlScoringClient;
                this.contextScorer = contextScorer;
                this.merchantRepository = merchantRepository;
        }

        // @Transactional
        // public TransactionResponse create(String userEmail, CreateTransactionRequest
        // request) {

        // Account account = accountRepository.findById(request.accountId())
        // .orElseThrow(() -> new ResponseStatusException(
        // HttpStatus.NOT_FOUND, "Account not found"));

        // if (!account.getUser().getEmail().equals(userEmail)) {
        // throw new ResponseStatusException(
        // HttpStatus.FORBIDDEN, "This account does not belong to you");
        // }

        // UserProfile profile = userProfileRepository
        // .findByUser_Id(account.getUser().getId())
        // .orElseThrow(() -> new ResponseStatusException(
        // HttpStatus.UNPROCESSABLE_ENTITY,
        // "No cardholder profile on file for scoring"));

        // // --- Resolve the merchant (by id, phone, or email) ---
        // Merchant merchant = resolveMerchant(request);

        // // --- Funds checks (before any scoring work) ---
        // BigDecimal amount = request.amount();
        // if (amount == null || amount.signum() <= 0) {
        // throw new ResponseStatusException(
        // HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        // }
        // if (account.getStatus() != AccountStatus.ACTIVE) {
        // throw new ResponseStatusException(
        // HttpStatus.FORBIDDEN, "Account is not active");
        // }
        // if (account.getBalance().compareTo(amount) < 0) {
        // throw new ResponseStatusException(
        // HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient balance");
        // }

        // // The merchant supplies the category + location the scorer needs.
        // String categoryCode = merchant.getCategory().getCode(); // e.g. "grocery_pos"
        // double merchLat = merchant.getLat().doubleValue();
        // double merchLon = merchant.getLon().doubleValue();

        // OffsetDateTime txnTime = request.transactionTime() != null
        // ? request.transactionTime()
        // : OffsetDateTime.now();

        // Transaction txn = new Transaction();
        // txn.setAccount(account);
        // txn.setAmount(request.amount());
        // txn.setMerchant(merchant.getName()); // from the merchant
        // txn.setMerchantCategory(categoryCode); // from the merchant's category
        // txn.setChannel(request.channel());
        // txn.setLocationLat(merchant.getLat()); // from the merchant
        // txn.setLocationLon(merchant.getLon()); // from the merchant
        // txn.setDeviceId(request.deviceId());
        // txn.setTransactionTime(txnTime);
        // txn.setStatus(TransactionStatus.PENDING);

        // // --- Layer 1: ML scoring ---
        // MlPredictRequest mlRequest = new MlPredictRequest(
        // txnTime.format(ML_TS),
        // profile.getDob().toString(),
        // request.amount().doubleValue(),
        // categoryCode, // merchant's category
        // profile.getHomeLat().doubleValue(),
        // profile.getHomeLon().doubleValue(),
        // merchLat, // merchant's location
        // merchLon,
        // profile.getGender(),
        // profile.getCityPop());

        // MlPredictResponse ml = mlScoringClient.predict(mlRequest);
        // double mlScore = ml.fraudScore();

        // // --- Layers 2 & 3: context scoring ---
        // double distanceKm = haversineKm(
        // profile.getHomeLat().doubleValue(), profile.getHomeLon().doubleValue(),
        // merchLat, merchLon); // merchant's location

        // // velocity: transactions for this account in the last 5 minutes of REAL time
        // long recentCount =
        // transactionRepository.countByAccountIdAndTransactionTimeAfter(
        // account.getId(), OffsetDateTime.now().minusMinutes(5));

        // ContextResult context = contextScorer.score(txn, profile, distanceKm,
        // recentCount);

        // // --- Blend: context can escalate, not rescue ML's strong catches ---
        // double finalScore = blend(mlScore, context.score());

        // // Behavioral escalation floor. A SINGLE strong signal lifts the
        // // transaction to at least YELLOW (monitor); MULTIPLE stacking signals
        // // escalate to ORANGE (step-up verification). This mirrors risk-based
        // // auth: one anomaly is suspicious, several together demand verification.
        // if (context.score() >= 0.45) {
        // finalScore = Math.max(finalScore, 0.90); // -> ORANGE (step-up / OTP)
        // } else if (context.score() >= 0.22) {
        // finalScore = Math.max(finalScore, 0.55); // -> YELLOW (monitor)
        // }

        // // Log the breakdown so we can see the layers working (and for the demo).
        // log.info(">>> SCORING txn amount={} | ML={} context={} final={} reasons={}",
        // request.amount(), String.format("%.4f", mlScore),
        // String.format("%.4f", context.score()),
        // String.format("%.4f", finalScore), context.reasons());

        // // --- Map final score -> risk band (same thresholds as the ML service) ---
        // RiskLevel risk = bandFor(finalScore);
        // txn.setRiskLevel(risk);
        // txn.setIsolationForestScore(ml.isolationForestScore());
        // txn.setXgboostProbability(ml.xgboostProbability());
        // txn.setFraudScore(finalScore); // store the BLENDED score
        // txn.setFlagReasons(context.reasons());

        // String decision;
        // switch (risk) {
        // case GREEN, YELLOW -> {
        // txn.setStatus(TransactionStatus.APPROVED);
        // decision = "APPROVED";
        // }
        // case ORANGE -> {
        // txn.setStatus(TransactionStatus.PENDING); // awaiting OTP
        // decision = "OTP_REQUIRED";
        // }
        // case RED -> {
        // txn.setStatus(TransactionStatus.BLOCKED);
        // decision = "DECLINED";
        // }
        // default -> decision = "APPROVED";
        // }

        // Transaction saved = transactionRepository.save(txn);

        // return new TransactionResponse(
        // saved.getId(),
        // saved.getStatus().name(),
        // saved.getRiskLevel().name(),
        // decision);
        // }
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

                // --- Resolve the merchant (by id, phone, or email) ---
                Merchant merchant = resolveMerchant(request);

                // --- Funds checks (before any scoring work) ---
                BigDecimal amount = request.amount();
                if (amount == null || amount.signum() <= 0) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
                }
                if (account.getStatus() != AccountStatus.ACTIVE) {
                        throw new ResponseStatusException(
                                        HttpStatus.FORBIDDEN, "Account is not active");
                }
                if (account.getBalance().compareTo(amount) < 0) {
                        throw new ResponseStatusException(
                                        HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient balance");
                }
                account.setBalance(account.getBalance().subtract(amount));
                accountRepository.save(account);

                // The merchant supplies the category + location the scorer needs.
                String categoryCode = merchant.getCategory().getCode(); // e.g. "grocery_pos"
                double merchLat = merchant.getLat().doubleValue();
                double merchLon = merchant.getLon().doubleValue();

                OffsetDateTime txnTime = request.transactionTime() != null
                                ? request.transactionTime()
                                : OffsetDateTime.now();

                Transaction txn = new Transaction();
                txn.setAccount(account);
                txn.setAmount(request.amount());
                txn.setMerchant(merchant.getName()); // from the merchant
                txn.setMerchantCategory(categoryCode); // from the merchant's category
                txn.setChannel(request.channel());
                txn.setLocationLat(merchant.getLat()); // from the merchant
                txn.setLocationLon(merchant.getLon()); // from the merchant
                txn.setDeviceId(request.deviceId());
                txn.setTransactionTime(txnTime);
                txn.setStatus(TransactionStatus.PENDING);

                // --- Layer 1: ML scoring ---
                MlPredictRequest mlRequest = new MlPredictRequest(
                                txnTime.format(ML_TS),
                                profile.getDob().toString(),
                                request.amount().doubleValue(),
                                categoryCode, // merchant's category
                                profile.getHomeLat().doubleValue(),
                                profile.getHomeLon().doubleValue(),
                                merchLat, // merchant's location
                                merchLon,
                                profile.getGender(),
                                profile.getCityPop());

                MlPredictResponse ml = mlScoringClient.predict(mlRequest);
                double mlScore = ml.fraudScore();

                // --- Layers 2 & 3: context scoring ---
                double distanceKm = TravelRisk.haversineKm(
                                profile.getHomeLat().doubleValue(), profile.getHomeLon().doubleValue(),
                                merchLat, merchLon); // merchant's location

                // velocity: transactions for this account in the 5 minutes before this one
                long recentCount = transactionRepository.countByAccountIdAndTransactionTimeAfter(
                                account.getId(), txnTime.minusMinutes(5));

                ContextResult context = contextScorer.score(txn, profile, distanceKm, recentCount);

                // --- Travel context: compare with the last APPROVED purchase ---
                Optional<Transaction> lastTrusted = transactionRepository
                                .findFirstByAccountIdAndStatusAndTransactionTimeBeforeOrderByTransactionTimeDesc(
                                                account.getId(), TransactionStatus.APPROVED, txnTime);

                TravelRisk.Result travel = lastTrusted
                                .filter(p -> p.getLocationLat() != null && p.getLocationLon() != null)
                                .map(p -> TravelRisk.evaluate(
                                                p.getLocationLat().doubleValue(), p.getLocationLon().doubleValue(),
                                                p.getTransactionTime(), merchLat, merchLon, txnTime))
                                .orElse(TravelRisk.Result.none());

                double farHomeScore = lastTrusted.isPresent() ? 0.0 : TravelRisk.farFromHomeScore(distanceKm);

                // C = max(profile context, travel signal, far-from-home signal)
                double contextScore = Math.max(context.score(), Math.max(travel.score(), farHomeScore));

                // blend() already applies the escalation floors, so the old if/else block is
                // deleted
                double finalScore = blend(mlScore, contextScore);

                List<String> reasons = new ArrayList<>(context.reasons());
                if (travel.reason() != null) {
                        reasons.add(travel.reason());
                } else if (farHomeScore > 0) {
                        reasons.add(String.format("Purchase %.0f km from home with no earlier approved purchase",
                                        distanceKm));
                }

                // Behavioral escalation floor. A SINGLE strong signal lifts the
                // transaction to at least YELLOW (monitor); MULTIPLE stacking signals
                // escalate to ORANGE (step-up verification). This mirrors risk-based
                // auth: one anomaly is suspicious, several together demand verification.
                if (context.score() >= 0.45) {
                        finalScore = Math.max(finalScore, 0.90); // -> ORANGE (step-up / OTP)
                } else if (context.score() >= 0.22) {
                        finalScore = Math.max(finalScore, 0.55); // -> YELLOW (monitor)
                }

                // Log the breakdown so we can see the layers working (and for the demo).
                log.info(">>> SCORING txn amount={} | ML={} context={} travel={} final={} reasons={}",
                                request.amount(), String.format("%.4f", mlScore),
                                String.format("%.4f", context.score()),
                                String.format("%.2f", Math.max(travel.score(), farHomeScore)),
                                String.format("%.4f", finalScore), reasons);

                // --- Map final score -> risk band (same thresholds as the ML service) ---
                RiskLevel risk = bandFor(finalScore);
                txn.setRiskLevel(risk);
                txn.setIsolationForestScore(ml.isolationForestScore());
                txn.setXgboostProbability(ml.xgboostProbability());
                txn.setFraudScore(finalScore); // store the BLENDED score
                txn.setFlagReasons(reasons);

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
        static RiskLevel bandFor(double score) {
                if (score < 0.50)
                        return RiskLevel.GREEN;
                if (score < 0.80)
                        return RiskLevel.YELLOW;
                if (score < 0.95)
                        return RiskLevel.ORANGE;
                return RiskLevel.RED;
        }

        // Resolve the merchant from the request: id preferred, then phone, then email.
        private Merchant resolveMerchant(CreateTransactionRequest request) {
                if (request.merchantId() != null) {
                        return merchantRepository.findByIdWithCategory(request.merchantId())
                                        .orElseThrow(() -> new ResponseStatusException(
                                                        HttpStatus.NOT_FOUND, "Merchant not found"));
                }
                if (request.merchantPhone() != null && !request.merchantPhone().isBlank()) {
                        return merchantRepository.findByPhone(request.merchantPhone())
                                        .orElseThrow(() -> new ResponseStatusException(
                                                        HttpStatus.NOT_FOUND, "No merchant with that phone"));
                }
                if (request.merchantEmail() != null && !request.merchantEmail().isBlank()) {
                        return merchantRepository.findByEmail(request.merchantEmail())
                                        .orElseThrow(() -> new ResponseStatusException(
                                                        HttpStatus.NOT_FOUND, "No merchant with that email"));
                }
                throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Provide a merchantId, merchantPhone, or merchantEmail");
        }

        // add inside TransactionService
        static double blend(double mlScore, double contextScore) {
                double finalScore = Math.min(1.0, mlScore + contextScore * CONTEXT_BOOST);
                if (contextScore >= 0.45) {
                        finalScore = Math.max(finalScore, 0.90);
                } else if (contextScore >= 0.22) {
                        finalScore = Math.max(finalScore, 0.55);
                }
                return finalScore;
        }

}