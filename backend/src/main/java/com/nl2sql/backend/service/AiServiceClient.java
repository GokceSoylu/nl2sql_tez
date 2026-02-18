package com.nl2sql.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.backend.dto.Nl2SqlRequest;
import com.nl2sql.backend.dto.Nl2SqlResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
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

    public AiServiceClient(RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${ai.service.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    public Nl2SqlResponse translate(Nl2SqlRequest req) {
        try {
            // FastAPI'nin beklediği JSON payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("question", req.getQuestion());
            payload.put("language", req.getLanguage());
            payload.put("schema", req.getSchema());
            payload.put("context", null);

            // ✅ JSON string'e çevir (body kesin gider)
            String json = objectMapper.writeValueAsString(payload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

            HttpEntity<String> entity = new HttpEntity<>(json, headers);

            // debug: gerçekten body var mı görelim
            System.out.println("AI CLIENT CALLED ✅ baseUrl=" + baseUrl);
            System.out.println("AI REQUEST JSON LEN=" + json.length());
            System.out.println("AI REQUEST JSON=" + json);

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
