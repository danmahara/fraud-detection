package com.fraud.detection.ml;

import com.fraud.detection.ml.dto.MlPredictRequest;
import com.fraud.detection.ml.dto.MlPredictResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Component
public class MlScoringClient {

    private final RestClient restClient;
    private final String predictPath;

    public MlScoringClient(
            RestClient.Builder builder,
            @Value("${app.ml-service.base-url}") String baseUrl,
            @Value("${app.ml-service.predict-path}") String predictPath) {

        // The JDK HttpClient defaults to HTTP/2 and attempts an h2c upgrade on
        // cleartext http://, which uvicorn doesn't support -> it drops the body
        // and FastAPI sees an empty request. Pin the client to HTTP/1.1.
        HttpClient http11 = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(http11))
                .build();
        this.predictPath = predictPath;
    }

    public MlPredictResponse predict(MlPredictRequest request) {
        return restClient.post()
                .uri(predictPath)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MlPredictResponse.class);
    }
}