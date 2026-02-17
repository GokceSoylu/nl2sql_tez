package com.nl2sql.backend.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SqlExecutionService {

    private final JdbcTemplate jdbcTemplate;

    public SqlExecutionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> executeSelect(String sql) {
        return jdbcTemplate.queryForList(sql);
    }
}
