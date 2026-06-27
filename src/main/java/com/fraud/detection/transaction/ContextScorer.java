package com.fraud.detection.transaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fraud.detection.entity.Transaction;
import com.fraud.detection.entity.UserProfile;
import com.fraud.detection.transaction.dto.ContextResult;

/**
 * Layer 2 + 3: behavioural / context scoring.
 *
 * Compares a transaction against THIS user's normal behaviour (their profile)
 * and recent activity, producing a context risk score in [0, 1] plus the list
 * of reasons that fired. These are transparent weighted rules, NOT ML.
 *
 * Philosophy: no single signal blocks a transaction; they accumulate. One flag
 * is noise, several together is a pattern.
 */
@Component
public class ContextScorer {

    // Weights: how much we trust each signal. Tune these. They don't need to
    // sum to 1 — the final score is clamped to [0, 1].
    private static final double W_AMOUNT = 0.35;
    private static final double W_DISTANCE = 0.25;
    private static final double W_NEW_DEVICE = 0.25;
    private static final double W_VELOCITY = 0.20;
    private static final double W_ODD_HOUR = 0.10;

    /**
     * @param txn         the transaction being scored (amount, deviceId, time,
     *                    location already set)
     * @param profile     the cardholder's profile (avg/max spend, known device,
     *                    home)
     * @param distanceKm  distance from home to merchant (already computed for the
     *                    ML call)
     * @param recentCount number of this account's transactions in the recent window
     */
    public ContextResult score(Transaction txn, UserProfile profile,
            double distanceKm, long recentCount) {
        List<String> reasons = new ArrayList<>();

        double amount = amountAnomaly(txn.getAmount(), profile, reasons);
        double distance = distanceSignal(distanceKm, reasons);
        double newDevice = newDeviceSignal(txn.getDeviceId(), profile, reasons);
        double velocity = velocitySignal(recentCount, reasons);
        double oddHour = oddHourSignal(txn.getTransactionTime(), reasons);

        double raw = W_AMOUNT * amount +
                W_DISTANCE * distance +
                W_NEW_DEVICE * newDevice +
                W_VELOCITY * velocity +
                W_ODD_HOUR * oddHour;

        double score = Math.min(1.0, raw); // clamp to [0, 1]
        return new ContextResult(score, reasons);
    }

    // --- Signal 1: amount vs THIS user's normal spend ---
    private double amountAnomaly(BigDecimal amount, UserProfile profile, List<String> reasons) {
        if (profile.getAvgAmount() == null
                || profile.getAvgAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0; // no baseline yet -> can't judge
        }
        double amt = amount.doubleValue();
        double avg = profile.getAvgAmount().doubleValue();
        double ratio = amt / avg; // how many times their average?

        // ratio <= 3 -> normal (0). ratio >= 15 -> maxed (1). linear between.
        double signal = (ratio - 3.0) / (15.0 - 3.0);
        signal = Math.max(0.0, Math.min(1.0, signal));

        if (signal > 0.3) {
            reasons.add(String.format("Amount $%.2f is %.0fx this user's average", amt, ratio));
        }
        return signal;
    }

    // --- Signal 2: distance from home ---
    private double distanceSignal(double distanceKm, List<String> reasons) {
        // <100km -> normal (0). >3000km -> maxed (1). linear between.
        double signal = (distanceKm - 100.0) / (3000.0 - 100.0);
        signal = Math.max(0.0, Math.min(1.0, signal));

        if (signal > 0.3) {
            reasons.add(String.format("Purchase %.0f km from home", distanceKm));
        }
        return signal;
    }

    // --- Signal 3: new / unknown device ---
    private double newDeviceSignal(String deviceId, UserProfile profile, List<String> reasons) {
        String known = profile.getLastKnownDevice();
        if (deviceId == null || known == null) {
            return 0.0; // can't judge without both
        }
        if (!deviceId.equals(known)) {
            reasons.add("Device not recognised for this user");
            return 1.0; // binary: unknown device is a full flag
        }
        return 0.0;
    }

    // --- Signal 4: velocity (many transactions in a short window) ---
    private double velocitySignal(long recentCount, List<String> reasons) {
        // recentCount is transactions in the recent window (we pass last 5 min).
        // <=2 -> normal. >=8 -> maxed.
        double signal = (recentCount - 2.0) / (8.0 - 2.0);
        signal = Math.max(0.0, Math.min(1.0, signal));

        if (signal > 0.3) {
            reasons.add(recentCount + " transactions in the last few minutes");
        }
        return signal;
    }

    // --- Signal 5: odd hour (middle of the night) ---
    private double oddHourSignal(OffsetDateTime time, List<String> reasons) {
        if (time == null)
            return 0.0;
        int hour = time.getHour();
        // 1am–5am is the risky window.
        if (hour >= 1 && hour <= 5) {
            reasons.add("Transaction at " + hour + ":00 (unusual hour)");
            return 1.0;
        }
        return 0.0;
    }
}
