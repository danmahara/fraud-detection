package com.fraud.detection.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MlPredictResponse(
                @JsonProperty("isolation_forest_score") double isolationForestScore,
                @JsonProperty("xgboost_probability") double xgboostProbability,
                @JsonProperty("fraud_score") double fraudScore,
                @JsonProperty("risk_level") String riskLevel,
                @JsonProperty("decision") String decision) {
}