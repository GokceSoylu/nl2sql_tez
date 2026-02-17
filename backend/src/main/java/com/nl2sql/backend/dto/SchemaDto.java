package com.nl2sql.backend.dto;

import java.util.List;

public class SchemaDto {

    private List<TableDto> tables;

    public SchemaDto() {
    }

    public SchemaDto(List<TableDto> tables) {
        this.tables = tables;
    }

    public List<TableDto> getTables() {
        return tables;
    }

    public void setTables(List<TableDto> tables) {
        this.tables = tables;
    }

    public static class TableDto {
        private String name;
        private List<String> columns;

        public TableDto() {
        }

        public TableDto(String name, List<String> columns) {
            this.name = name;
            this.columns = columns;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getColumns() {
            return columns;
        }

        public void setColumns(List<String> columns) {
            this.columns = columns;
        }
    }
}
