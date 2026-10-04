package com.fraud.detection.transaction;

import java.time.Duration;
import java.time.OffsetDateTime;

public final class TravelRisk {

    static final double MIN_KM = 50.0;
    static final double IMPOSSIBLE_KMH = 900.0; // faster than a commercial jet
    static final double FLIGHT_KMH = 250.0; // needs a flight
    static final double FAR_FROM_HOME_KM = 5000.0;

    public record Result(double score, double km, double speedKmh, String reason) {
        static Result none() {
            return new Result(0.0, 0.0, 0.0, null);
        }
    }

    private TravelRisk() {
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // Compares this purchase with the last trusted (approved) one.
    static Result evaluate(double prevLat, double prevLon, OffsetDateTime prevTime,
            double lat, double lon, OffsetDateTime time) {
        double km = haversineKm(prevLat, prevLon, lat, lon);
        if (km < MIN_KM) {
            return Result.none();
        }
        long seconds = Math.max(1, Duration.between(prevTime, time).getSeconds());
        double speed = km / (seconds / 3600.0);

        double score = 0.0;
        if (speed > IMPOSSIBLE_KMH) {
            score = 1.0;
        } else if (speed > FLIGHT_KMH) {
            score = 0.3;
        }
        if (score == 0.0) {
            return Result.none();
        }
        String reason = String.format(
                "%s: %.0f km from last approved purchase in %s (%.0f km/h)",
                score == 1.0 ? "Impossible travel" : "Fast travel",
                km, formatGap(seconds), speed);
        return new Result(score, km, speed, reason);
    }

    // Used when there is no earlier approved purchase to compare with.
    static double farFromHomeScore(double distanceFromHomeKm) {
        return distanceFromHomeKm >= FAR_FROM_HOME_KM ? 0.5 : 0.0;
    }

    private static String formatGap(long seconds) {
        if (seconds < 120)
            return seconds + " s";
        if (seconds < 7200)
            return (seconds / 60) + " min";
        return String.format("%.1f h", seconds / 3600.0);
    }
}