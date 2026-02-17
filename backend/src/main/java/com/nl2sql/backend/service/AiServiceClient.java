package com.nl2sql.backend.service;

import com.nl2sql.backend.dto.Nl2SqlRequest;
import com.nl2sql.backend.dto.Nl2SqlResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AiServiceClient {

    private final RestClient restClient;

    public AiServiceClient(@Value("${ai.service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public Nl2SqlResponse translate(Nl2SqlRequest req) {
        return restClient.post()
                .uri("/generate-sql")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(Nl2SqlResponse.class);
    }
}
