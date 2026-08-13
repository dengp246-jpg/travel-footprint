package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.User;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.PersonalDataExportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DataExportController {
    private final CurrentUserService currentUserService;
    private final PersonalDataExportService exportService;

    public DataExportController(CurrentUserService currentUserService, PersonalDataExportService exportService) {
        this.currentUserService = currentUserService; this.exportService = exportService;
    }

    @GetMapping("/settings/export")
    public ResponseEntity<byte[]> export(HttpSession session) throws JsonProcessingException {
        User user = currentUserService.getCurrentUser(session);
        if (user == null) return ResponseEntity.status(401).build();
        String filename = "travel-footprint-export-" + LocalDate.now() + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                .body(exportService.export(user));
    }
}
