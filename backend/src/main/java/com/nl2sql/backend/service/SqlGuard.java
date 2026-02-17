package com.nl2sql.backend.service;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SqlGuard {

    private static final Set<String> FORBIDDEN = Set.of(
            "drop", "delete", "update", "insert", "alter", "truncate", "create", "grant", "revoke");

    public void validate(String sql) {
        String s = sql.trim().toLowerCase();

        // sadece SELECT
        if (!s.startsWith("select")) {
            throw new IllegalArgumentException("Only SELECT queries are allowed.");
        }

        // çoklu statement engeli (sondaki ; hariç)
        String withoutLastSemicolon = s.endsWith(";") ? s.substring(0, s.length() - 1) : s;
        if (withoutLastSemicolon.contains(";")) {
            throw new IllegalArgumentException("Multiple statements are not allowed.");
        }

        // yasaklı keyword
        for (String kw : FORBIDDEN) {
            if (s.contains(kw + " ") || s.contains("\n" + kw + " ")) {
                throw new IllegalArgumentException("Forbidden keyword detected: " + kw);
            }
        }
    }

    public String enforceLimit(String sql, int maxRows) {
        String s = sql.trim();
        String lower = s.toLowerCase();
        if (lower.contains("limit"))
            return s;
        if (!s.endsWith(";"))
            s += ";";
        return s.substring(0, s.length() - 1) + "\nLIMIT " + maxRows + ";";
    }
}
