package com.example.travelfootprint.service;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AndroidAppPackageService {

    private final Path apkPath;

    public AndroidAppPackageService(
            @Value("${app.android-apk-path:outputs/travel-footprint-android-debug.apk}") String apkPath) {
        this.apkPath = Path.of(apkPath).toAbsolutePath().normalize();
    }

    public boolean isAvailable() {
        return Files.isRegularFile(apkPath) && Files.isReadable(apkPath);
    }

    public Path path() {
        return apkPath;
    }
}
