package com.fraud.detection.admin.dto;

import java.util.List;

public record StatsResponse(
        long totalTransactions,
        long flaggedCount,
        double flaggedRate, // flagged / total, 0..1
        List<CountItem> byRiskLevel, // for the risk distribution chart
        List<CountItem> byCategory // for the category chart
) {
    // A reusable {label, count} pair for charts.
    public record CountItem(String label, long count) {
    }
}