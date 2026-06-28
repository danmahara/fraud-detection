package com.fraud.detection.admin.dto;

import java.util.List;

public record PagedAdminTransactions(
        List<AdminTransactionView> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}