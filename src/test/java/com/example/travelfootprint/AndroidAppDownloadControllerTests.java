package com.example.travelfootprint;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.travelfootprint.controller.AndroidAppDownloadController;
import com.example.travelfootprint.service.AndroidAppPackageService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

class AndroidAppDownloadControllerTests {

    @TempDir
    Path tempDirectory;

    @Test
    void servesApkWithInstallableContentTypeAndAttachmentName() throws Exception {
        Path apk = tempDirectory.resolve("travel.apk");
        byte[] content = new byte[] {0x50, 0x4b, 0x03, 0x04};
        Files.write(apk, content);

        ResponseEntity<Resource> response = new AndroidAppDownloadController(
                new AndroidAppPackageService(apk.toString())).downloadAndroidApp();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/vnd.android.package-archive");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(content.length);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("travel-footprint-android.apk");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getInputStream().readAllBytes()).isEqualTo(content);
    }

    @Test
    void returnsNotFoundWhenApkHasNotBeenBuilt() throws Exception {
        Path missing = tempDirectory.resolve("missing.apk");

        ResponseEntity<Resource> response = new AndroidAppDownloadController(
                new AndroidAppPackageService(missing.toString())).downloadAndroidApp();

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNull();
    }
}
