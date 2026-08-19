package com.example.travelfootprint.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import com.example.travelfootprint.service.AndroidAppPackageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AndroidAppDownloadController {

    private static final MediaType APK_MEDIA_TYPE = MediaType.parseMediaType("application/vnd.android.package-archive");
    private static final String DOWNLOAD_NAME = "travel-footprint-android.apk";

    private final AndroidAppPackageService androidAppPackageService;

    public AndroidAppDownloadController(AndroidAppPackageService androidAppPackageService) {
        this.androidAppPackageService = androidAppPackageService;
    }

    @GetMapping("/download/android")
    public ResponseEntity<Resource> downloadAndroidApp() throws IOException {
        if (!androidAppPackageService.isAvailable()) {
            return ResponseEntity.notFound().build();
        }

        var apkPath = androidAppPackageService.path();
        Resource apk = new FileSystemResource(apkPath);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(DOWNLOAD_NAME, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(APK_MEDIA_TYPE)
                .contentLength(Files.size(apkPath))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-APK-Version", "1.0.0-debug")
                .body(apk);
    }
}
