package com.nl2sql.backend.controller;

import com.nl2sql.backend.dto.SchemaDto;
import com.nl2sql.backend.service.SchemaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SchemaController {

    private final SchemaService schemaService;

    public SchemaController(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @GetMapping("/schema")
    public SchemaDto schema() {
        return schemaService.loadPublicSchema();
    }
}
