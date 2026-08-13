package com.example.travelfootprint.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import javax.sql.DataSource;
import com.example.travelfootprint.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final DataSource dataSource;
    private final Path uploadRoot;
    private final FileStorageService fileStorageService;

    public HealthController(DataSource dataSource, @Value("${app.upload-dir:uploads}") String uploadDir,
            FileStorageService fileStorageService) {
        this.dataSource = dataSource;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthStatus> health() {
        boolean databaseReady = databaseReady();
        boolean storageReady = fileStorageService.isReady();
        boolean ready = databaseReady && storageReady;
        HealthStatus status = new HealthStatus(ready ? "UP" : "DEGRADED", databaseReady, storageReady, Instant.now());
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(status);
    }

    private boolean databaseReady() {
        try (var connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (Exception exception) {
            return false;
        }
    }

    public record HealthStatus(String status, boolean database, boolean storage, Instant timestamp) { }
}
