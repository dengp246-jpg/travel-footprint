package com.example.travelfootprint;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.travelfootprint.model.StoredImage;
import com.example.travelfootprint.repository.StoredImageRepository;
import com.example.travelfootprint.service.FileStorageService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class FileStorageServiceTests {

    @TempDir
    Path uploadDirectory;

    @Test
    void rejectsFileWhoseContentDoesNotMatchDeclaredImageType() throws IOException {
        FileStorageService storageService = new FileStorageService(uploadDirectory.toString(), 5 * 1024 * 1024);
        MockMultipartFile fakeImage = new MockMultipartFile(
                "photo",
                "photo.png",
                "image/png",
                "this is not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(IOException.class, () -> storageService.store(fakeImage, "posts"));
    }

    @Test
    void storesValidatedPngInsideConfiguredUploadDirectory() throws IOException {
        FileStorageService storageService = new FileStorageService(uploadDirectory.toString(), 5 * 1024 * 1024);
        byte[] pngHeader = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        };
        MockMultipartFile image = new MockMultipartFile("photo", "photo.png", "image/png", pngHeader);

        String storedPath = storageService.store(image, "posts");

        assertTrue(storedPath.startsWith("/uploads/posts/"));
        assertTrue(java.nio.file.Files.exists(
                uploadDirectory.resolve(storedPath.substring("/uploads/".length()).replace('/', java.io.File.separatorChar))));
    }

    @Test
    void storesAndLoadsValidatedImageThroughDatabaseMode() throws IOException {
        StoredImageRepository repository = mock(StoredImageRepository.class);
        when(repository.save(any(StoredImage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        FileStorageService storageService = new FileStorageService(
                uploadDirectory.toString(), 5 * 1024 * 1024, "database", repository);
        byte[] pngHeader = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        };
        MockMultipartFile image = new MockMultipartFile("photo", "photo.png", "image/png", pngHeader);

        String storedPath = storageService.store(image, "posts");
        StoredImage stored = new StoredImage();
        stored.setPublicPath(storedPath);
        stored.setContentType("image/png");
        stored.setContent(pngHeader);
        when(repository.findByPublicPath(storedPath)).thenReturn(Optional.of(stored));

        FileStorageService.StoredFile loaded = storageService.load(storedPath);

        assertTrue(storedPath.startsWith("/uploads/posts/"));
        assertEquals("image/png", loaded.contentType());
        assertArrayEquals(pngHeader, loaded.content());
    }

    @Test
    void sizeErrorUsesConfiguredCloudLimit() throws IOException {
        FileStorageService storageService = new FileStorageService(uploadDirectory.toString(), 8);
        MockMultipartFile image = new MockMultipartFile(
                "photo", "photo.png", "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00});

        IOException exception = assertThrows(IOException.class, () -> storageService.store(image, "posts"));

        assertTrue(exception.getMessage().contains("1KB"));
    }
}
