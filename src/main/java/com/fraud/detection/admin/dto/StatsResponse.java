package com.fraud.detection.admin.dto;

import java.util.List;

public record StatsResponse(
        long totalTransactions,
        long flaggedCount,
        long blockedCount,
        long todayTransactions,
        long todayFlagged,
        long todayBlocked,
        double flaggedRate,
        List<CountItem> byRiskLevel,
        List<CountItem> byCategory) {
    public record CountItem(String label, long count) {
    }
}