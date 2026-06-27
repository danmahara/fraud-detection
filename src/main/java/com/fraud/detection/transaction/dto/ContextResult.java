package com.fraud.detection.transaction.dto;

import java.util.List;

// The output of the context layer: a 0..1 score plus the list of signals
// that fired, so decisions are explainable ("new device", "unusual amount").
public record ContextResult(
        double score,
        List<String> reasons) {
}
