package com.nl2sql.backend.dto;

import java.util.List;
import java.util.Map;

public class Nl2SqlResponse {
    private String sql;
    private List<Map<String, Object>> rows;
    private String error; // optional

    public Nl2SqlResponse() {
    }

    public Nl2SqlResponse(String sql, List<Map<String, Object>> rows) {
        this.sql = sql;
        this.rows = rows;
        this.error = null;
    }

    public Nl2SqlResponse(String sql, List<Map<String, Object>> rows, String error) {
        this.sql = sql;
        this.rows = rows;
        this.error = error;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public void setRows(List<Map<String, Object>> rows) {
        this.rows = rows;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
