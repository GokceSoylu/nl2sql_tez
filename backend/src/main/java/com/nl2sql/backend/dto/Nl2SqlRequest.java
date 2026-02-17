package com.nl2sql.backend.dto;

public class Nl2SqlRequest {

    private String question;
    private SchemaDto schema; // opsiyonel (AI kabul ediyorsa kullan)

    public Nl2SqlRequest() {
    }

    public Nl2SqlRequest(String question, SchemaDto schema) {
        this.question = question;
        this.schema = schema;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public SchemaDto getSchema() {
        return schema;
    }

    public void setSchema(SchemaDto schema) {
        this.schema = schema;
    }
}
