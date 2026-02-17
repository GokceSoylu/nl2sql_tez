package com.nl2sql.backend.controller;

import com.nl2sql.backend.dto.Nl2SqlRequest;
import com.nl2sql.backend.dto.Nl2SqlResponse;
import com.nl2sql.backend.dto.SchemaDto;
import com.nl2sql.backend.service.AiServiceClient;
import com.nl2sql.backend.service.SchemaService;
import com.nl2sql.backend.service.SqlExecutionService;
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

    private static final Set<String> FORBIDDEN = Set.of(
            "drop", "delete", "update", "insert", "alter", "truncate", "create", "grant", "revoke");

    public Nl2SqlController(
            AiServiceClient aiServiceClient,
            SchemaService schemaService,
            SqlExecutionService sqlExecutionService) {
        this.aiServiceClient = aiServiceClient;
        this.schemaService = schemaService;
        this.sqlExecutionService = sqlExecutionService;
    }

    // 1) SADECE SQL ÜRET: rows dönmez
    @PostMapping("/nl2sql")
    public ResponseEntity<Nl2SqlResponse> nl2sql(@RequestBody Nl2SqlRequest req) {
        return generateSql(req);
    }

    // 2) SQL ÜRET + DB'DE ÇALIŞTIR: rows döner
    @PostMapping("/ask")
    public ResponseEntity<Nl2SqlResponse> ask(@RequestBody Nl2SqlRequest req) {

        ResponseEntity<Nl2SqlResponse> gen = generateSql(req);
        if (!gen.getStatusCode().is2xxSuccessful() || gen.getBody() == null) {
            return gen;
        }

        String sql = gen.getBody().getSql();
        if (sql == null || sql.trim().isEmpty()) {
            return ResponseEntity.status(502).body(new Nl2SqlResponse("", List.of()));
        }

        // limit yoksa ekle (max 100)
        String lower = sql.trim().toLowerCase();
        if (!lower.contains("limit")) {
            sql = sql.trim();
            if (!sql.endsWith(";"))
                sql += ";";
            sql = sql.substring(0, sql.length() - 1) + "\nLIMIT 100;";
        }

        List<Map<String, Object>> rows = sqlExecutionService.executeSelect(sql);
        return ResponseEntity.ok(new Nl2SqlResponse(sql, rows));
    }

    // Ortak fonksiyon: schema ekle -> AI -> güvenlik -> SQL dön
    private ResponseEntity<Nl2SqlResponse> generateSql(Nl2SqlRequest req) {

        if (req.getQuestion() == null || req.getQuestion().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new Nl2SqlResponse("", List.of()));
        }

        // schema ekle
        SchemaDto schema = schemaService.loadPublicSchema();
        req.setSchema(schema);

        // AI -> SQL
        Nl2SqlResponse aiResp = aiServiceClient.translate(req);
        String sql = (aiResp != null) ? aiResp.getSql() : null;

        if (sql == null || sql.trim().isEmpty()) {
            return ResponseEntity.status(502).body(new Nl2SqlResponse("", List.of()));
        }

        sql = sql.trim();
        String lower = sql.toLowerCase();

        // sadece SELECT
        if (!lower.startsWith("select")) {
            return ResponseEntity.status(400).body(new Nl2SqlResponse(sql, List.of()));
        }

        // çoklu statement engeli (sondaki ; hariç)
        String withoutLastSemicolon = lower.endsWith(";") ? lower.substring(0, lower.length() - 1) : lower;
        if (withoutLastSemicolon.contains(";")) {
            return ResponseEntity.status(400).body(new Nl2SqlResponse(sql, List.of()));
        }

        // yasaklı keyword engeli
        for (String kw : FORBIDDEN) {
            if (lower.contains(kw + " ") || lower.contains("\n" + kw + " ")) {
                return ResponseEntity.status(400).body(new Nl2SqlResponse(sql, List.of()));
            }
        }

        // /nl2sql sadece SQL dönecek → rows boş
        return ResponseEntity.ok(new Nl2SqlResponse(sql, List.of()));
    }
}
