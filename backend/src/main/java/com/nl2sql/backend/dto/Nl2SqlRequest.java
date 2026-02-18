package com.nl2sql.backend.dto;

import java.util.List;
import com.nl2sql.backend.dto.SchemaDto.TableDto;

public class Nl2SqlRequest {

    private String question;
    private String language; // "tr" or "en"
    private List<TableDto> schema; // optional

    public Nl2SqlRequest() {
    }

    public Nl2SqlRequest(String question, String language, List<TableDto> schema) {
        this.question = question;
        this.language = language;
        this.schema = schema;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public List<TableDto> getSchema() {
        return schema;
    }

    // ✅ DÜZELTİLEN KISIM: SchemaDto değil List<TableDto> almalı
    public void setSchema(List<TableDto> schema) {
        this.schema = schema;
    }
}
