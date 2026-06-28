package com.fraud.detection.me.dto;

public record MyStatsView(
        long total,
        long approved, // GREEN + YELLOW
        long flagged, // ORANGE (needs verification)
        long blocked // RED
) {
}