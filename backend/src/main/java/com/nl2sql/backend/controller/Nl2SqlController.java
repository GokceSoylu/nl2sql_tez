package com.nl2sql.backend.controller;

import com.nl2sql.backend.dto.Nl2SqlRequest;
import com.nl2sql.backend.dto.Nl2SqlResponse;
import com.nl2sql.backend.dto.SchemaDto;
import com.nl2sql.backend.service.AiServiceClient;
import com.nl2sql.backend.service.MemoryService;
import com.nl2sql.backend.service.SchemaService;
import com.nl2sql.backend.service.SqlExecutionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
public class Nl2SqlController {

    private final AiServiceClient aiServiceClient;
    private final SchemaService schemaService;
    private final SqlExecutionService sqlExecutionService;
    private final MemoryService memoryService;

    private static final Set<String> FORBIDDEN = Set.of(
            "drop", "delete", "update", "insert", "alter", "truncate", "create", "grant", "revoke");

    public Nl2SqlController(
            AiServiceClient aiServiceClient,
            SchemaService schemaService,
            SqlExecutionService sqlExecutionService,
            MemoryService memoryService) {
        this.aiServiceClient = aiServiceClient;
        this.schemaService = schemaService;
        this.sqlExecutionService = sqlExecutionService;
        this.memoryService = memoryService;
    }

    // 1) SADECE SQL ÜRET: rows dönmez
    @PostMapping("/nl2sql")
    public ResponseEntity<Nl2SqlResponse> nl2sql(@RequestBody Nl2SqlRequest req, HttpServletRequest httpReq) {
        return generateSql(req, httpReq);
    }

    // 2) SQL ÜRET + DB'DE ÇALIŞTIR: rows döner
    @PostMapping("/ask")
    public ResponseEntity<Nl2SqlResponse> ask(@RequestBody Nl2SqlRequest req, HttpServletRequest httpReq) {

        String sessionId = httpReq.getSession(true).getId();

        // schema boş geldiyse backend tarafında otomatik doldur
        if (req.getSchema() == null || req.getSchema().isEmpty()) {
            SchemaDto s = schemaService.loadPublicSchema();
            req.setSchema(s.getTables());
        }

        ResponseEntity<Nl2SqlResponse> gen = generateSql(req, httpReq);
        if (!gen.getStatusCode().is2xxSuccessful() || gen.getBody() == null) {
            // generateSql içinde hata varsa onu döndür
            return gen;
        }

        String sql = gen.getBody().getSql();
        if (sql == null || sql.trim().isEmpty()) {
            memoryService.update(sessionId, req.getQuestion(), "", List.of(), "AI returned empty SQL");
            return ResponseEntity.status(502)
                    .body(new Nl2SqlResponse("", List.of(), "AI returned empty SQL"));
        }

        String normalized = sql.trim().toLowerCase();

        // sadece SELECT çalıştır
        if (!normalized.startsWith("select")) {
            memoryService.update(sessionId, req.getQuestion(), sql, List.of(), "Only SELECT queries are allowed");
            return ResponseEntity.badRequest()
                    .body(new Nl2SqlResponse(sql, List.of(), "Only SELECT queries are allowed"));
        }

        // forbidden keyword kontrol (ek güvenlik)
        for (String bad : FORBIDDEN) {
            if (normalized.contains(bad)) {
                String msg = "Forbidden keyword detected: " + bad;
                memoryService.update(sessionId, req.getQuestion(), sql, List.of(), msg);
                return ResponseEntity.badRequest()
                        .body(new Nl2SqlResponse(sql, List.of(), msg));
            }
        }

        // limit yoksa ekle (max 100)
        if (!normalized.contains("limit")) {
            sql = sql.trim();
            if (!sql.endsWith(";"))
                sql += ";";
            sql = sql.substring(0, sql.length() - 1) + "\nLIMIT 100;";
        }

        try {
            List<Map<String, Object>> rows = sqlExecutionService.executeSelect(sql);
            memoryService.update(sessionId, req.getQuestion(), sql, rows, null);
            return ResponseEntity.ok(new Nl2SqlResponse(sql, rows));
        } catch (Exception ex) {
            String msg = ex.getMessage();
            if (msg == null || msg.isBlank())
                msg = "SQL execution failed";

            memoryService.update(sessionId, req.getQuestion(), sql, List.of(), msg);

            // 422: SQL üretildi ama DB'de çalışmadı (ör: kolon yok)
            return ResponseEntity.unprocessableEntity()
                    .body(new Nl2SqlResponse(sql, List.of(), msg));
        }
    }

    // Ortak fonksiyon: schema ekle -> memory context ekle -> AI -> güvenlik -> SQL
    // dön
    private ResponseEntity<Nl2SqlResponse> generateSql(Nl2SqlRequest req, HttpServletRequest httpReq) {

        String sessionId = httpReq.getSession(true).getId();

        if (req.getQuestion() == null || req.getQuestion().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new Nl2SqlResponse("", List.of()));
        }

        // schema ekle
        SchemaDto schema = schemaService.loadPublicSchema();
        req.setSchema(schema.getTables());

        // ✅ memory context ekle
        var mem = memoryService.getOrCreate(sessionId);
        req.setContext(memoryService.toContextMap(mem));

        // AI -> SQL
        Nl2SqlResponse aiResp;
        try {
            aiResp = aiServiceClient.translate(req);
        } catch (Exception ex) {
            String msg = ex.getMessage();
            if (msg == null || msg.isBlank())
                msg = "AI service call failed";

            // SQL daha yok -> "" ile kaydediyoruz
            memoryService.update(sessionId, req.getQuestion(), "", List.of(), msg);

            return ResponseEntity.status(502)
                    .body(new Nl2SqlResponse("", List.of(), msg));
        }

        String sql = (aiResp != null) ? aiResp.getSql() : null;

        if (sql == null || sql.trim().isEmpty()) {
            memoryService.update(sessionId, req.getQuestion(), "", List.of(), "AI returned empty SQL");
            return ResponseEntity.status(502).body(new Nl2SqlResponse("", List.of(), "AI returned empty SQL"));
        }

        sql = sql.trim();
        String lower = sql.toLowerCase();

        // sadece SELECT
        if (!lower.startsWith("select")) {
            memoryService.update(sessionId, req.getQuestion(), sql, List.of(), "Only SELECT queries are allowed");
            return ResponseEntity.status(400)
                    .body(new Nl2SqlResponse(sql, List.of(), "Only SELECT queries are allowed"));
        }

        // çoklu statement engeli (sondaki ; hariç)
        String withoutLastSemicolon = lower.endsWith(";")
                ? lower.substring(0, lower.length() - 1)
                : lower;
        if (withoutLastSemicolon.contains(";")) {
            memoryService.update(sessionId, req.getQuestion(), sql, List.of(), "Multiple statements are not allowed");
            return ResponseEntity.status(400)
                    .body(new Nl2SqlResponse(sql, List.of(), "Multiple statements are not allowed"));
        }

        // yasaklı keyword engeli
        for (String kw : FORBIDDEN) {
            if (lower.contains(kw + " ") || lower.contains("\n" + kw + " ")) {
                String msg = "Forbidden keyword detected: " + kw;
                memoryService.update(sessionId, req.getQuestion(), sql, List.of(), msg);
                return ResponseEntity.status(400).body(new Nl2SqlResponse(sql, List.of(), msg));
            }
        }

        // ✅ SQL üretimi başarılı -> memory güncelle (rows yok)
        memoryService.update(sessionId, req.getQuestion(), sql, List.of(), null);

        return ResponseEntity.ok(new Nl2SqlResponse(sql, List.of()));
    }
}
