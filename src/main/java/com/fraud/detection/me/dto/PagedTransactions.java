package com.fraud.detection.me.dto;

import java.util.List;

public record PagedTransactions(
        List<MyTransactionView> content,
        int page, // current page (0-based)
        int size, // page size
        long totalElements, // total transactions
        int totalPages // total number of pages
) {
}