package com.fraud.detection.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MlPredictResponse(
        @JsonProperty("isolation_forest_score") Double isolationForestScore,
        @JsonProperty("xgboost_probability") Double xgboostProbability,
        @JsonProperty("fraud_score") Double fraudScore,
        @JsonProperty("risk_level") String riskLevel,
        @JsonProperty("decision") String decision) {
}