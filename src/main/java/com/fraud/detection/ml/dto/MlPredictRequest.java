package com.fraud.detection.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

public record MlPredictRequest(
                @JsonProperty("v_features") List<Double> vFeatures,
                @JsonProperty("amount") BigDecimal amount,
                @JsonProperty("merchant_category") String merchantCategory,
                @JsonProperty("channel") String channel,
                @JsonProperty("location_lat") BigDecimal locationLat,
                @JsonProperty("location_lon") BigDecimal locationLon,
                @JsonProperty("device_id") String deviceId) {
}