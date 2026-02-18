package com.nl2sql.backend.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MemoryService {

    // sessionId -> memory
    private final Map<String, MemoryState> store = new ConcurrentHashMap<>();

    public MemoryState getOrCreate(String sessionId) {
        return store.computeIfAbsent(sessionId, k -> new MemoryState());
    }

    public Map<String, Object> toContextMap(MemoryState st) {
        if (st == null)
            return null;

        Map<String, Object> ctx = new LinkedHashMap<>();
        if (st.lastQuestion != null)
            ctx.put("last_question", st.lastQuestion);
        if (st.lastSql != null)
            ctx.put("last_sql", st.lastSql);
        if (st.lastError != null)
            ctx.put("last_error", st.lastError);
        if (st.lastRowsPreview != null)
            ctx.put("last_rows_preview", st.lastRowsPreview);
        return ctx.isEmpty() ? null : ctx;
    }

    public void update(String sessionId, String question, String sql, List<Map<String, Object>> rows, String error) {
        MemoryState st = getOrCreate(sessionId);
        st.lastQuestion = question;
        st.lastSql = sql;
        st.lastError = (error == null || error.isBlank()) ? null : error;
        st.lastRowsPreview = buildPreview(rows);
    }

    private String buildPreview(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty())
            return "rows: []";

        // kolonlar + ilk 3 satır
        Map<String, Object> first = rows.get(0);
        List<String> cols = new ArrayList<>(first.keySet());
        int n = Math.min(3, rows.size());

        StringBuilder sb = new StringBuilder();
        sb.append("columns: ").append(cols).append("\n");
        sb.append("first_rows:\n");
        for (int i = 0; i < n; i++) {
            sb.append("  - ").append(rows.get(i)).append("\n");
        }
        return sb.toString();
    }

    public static class MemoryState {
        public String lastQuestion;
        public String lastSql;
        public String lastError;
        public String lastRowsPreview;
    }
}
