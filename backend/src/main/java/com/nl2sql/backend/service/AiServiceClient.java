package com.nl2sql.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.backend.dto.Nl2SqlRequest;
import com.nl2sql.backend.dto.Nl2SqlResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Service
public class AiServiceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public AiServiceClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${ai.service.base-url:https://nl2sql-ai-0n39.onrender.com}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;

        String cleanUrl = (baseUrl != null) ? baseUrl.trim() : "";
        if (cleanUrl.isEmpty() || cleanUrl.contains("localhost") || cleanUrl.contains("127.0.0.1")) {
            this.baseUrl = "https://nl2sql-ai-0n39.onrender.com";
        } else {
            this.baseUrl = cleanUrl.endsWith("/") ? cleanUrl.substring(0, cleanUrl.length() - 1) : cleanUrl;
        }
    }

    public Nl2SqlResponse translate(Nl2SqlRequest req) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("question", req.getQuestion());
            payload.put("language", req.getLanguage());
            payload.put("schema", req.getSchema());
            payload.put("context", null);

            String json = objectMapper.writeValueAsString(payload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

            HttpEntity<String> entity = new HttpEntity<>(json, headers);

            URI targetUri = URI.create(this.baseUrl + "/generate-sql");

            ResponseEntity<Nl2SqlResponse> resp = restTemplate.exchange(
                    targetUri,
                    HttpMethod.POST,
                    entity,
                    Nl2SqlResponse.class);

            return resp.getBody();

        } catch (Exception e) {
            throw new RuntimeException("AI call failed: " + e.getMessage(), e);
        }
    }
}