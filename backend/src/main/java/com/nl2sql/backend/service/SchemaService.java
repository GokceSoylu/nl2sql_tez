package com.nl2sql.backend.service;

import com.nl2sql.backend.dto.SchemaDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SchemaService {

    private final JdbcTemplate jdbcTemplate;

    public SchemaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SchemaDto loadPublicSchema() {
        String sql = """
                    SELECT table_name, column_name
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                    ORDER BY table_name, ordinal_position
                """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String table = (String) r.get("table_name");
            String col = (String) r.get("column_name");
            grouped.computeIfAbsent(table, k -> new ArrayList<>()).add(col);
        }

        List<SchemaDto.TableDto> tables = grouped.entrySet().stream()
                .map(e -> new SchemaDto.TableDto(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        return new SchemaDto(tables);
    }
}
