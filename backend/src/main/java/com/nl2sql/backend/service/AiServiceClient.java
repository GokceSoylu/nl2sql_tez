package com.nl2sql.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.backend.dto.Nl2SqlRequest;
import com.nl2sql.backend.dto.Nl2SqlResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

        // Localhost/127.0.0.1 gelirse veya boşsa Render canlı AI adresine yönlendir
        if (baseUrl == null || baseUrl.isBlank() || baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1")) {
            this.baseUrl = "https://nl2sql-ai-0n39.onrender.com";
        } else {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        }
    }

    public Nl2SqlResponse translate(Nl2SqlRequest req) {
        try {
            // FastAPI payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("question", req.getQuestion());
            payload.put("language", req.getLanguage());
            payload.put("schema", req.getSchema());
            payload.put("context", null);

            // JSON string serialize
            String json = objectMapper.writeValueAsString(payload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

            HttpEntity<String> entity = new HttpEntity<>(json, headers);

            System.out.println("AI CLIENT CALLED ✅ baseUrl=" + baseUrl);
            System.out.println("AI REQUEST JSON LEN=" + json.length());

            ResponseEntity<Nl2SqlResponse> resp = restTemplate.exchange(
                    baseUrl + "/generate-sql",
                    HttpMethod.POST,
                    entity,
                    Nl2SqlResponse.class);

            return resp.getBody();

        } catch (Exception e) {
            throw new RuntimeException("AI call failed: " + e.getMessage(), e);
        }
    }
}