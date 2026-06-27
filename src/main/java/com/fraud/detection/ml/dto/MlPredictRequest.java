package com.fraud.detection.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MlPredictRequest(
        @JsonProperty("trans_date_trans_time") String transDateTransTime,
        @JsonProperty("dob") String dob,
        @JsonProperty("amt") double amt,
        @JsonProperty("category") String category,
        @JsonProperty("lat") double lat, // cardholder home latitude
        @JsonProperty("long") double lon, // cardholder home longitude
        @JsonProperty("merch_lat") double merchLat, // where the purchase happened
        @JsonProperty("merch_long") double merchLon,
        @JsonProperty("gender") String gender,
        @JsonProperty("city_pop") int cityPop) {
}