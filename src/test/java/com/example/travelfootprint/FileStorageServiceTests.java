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

    @Test
    void storesValidatedMp4Video() throws IOException {
        FileStorageService storageService = new FileStorageService(uploadDirectory.toString(), 5 * 1024 * 1024);
        byte[] mp4Header = new byte[] {
                0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'
        };
        MockMultipartFile video = new MockMultipartFile("video", "journey.mp4", "video/mp4", mp4Header);

        String storedPath = storageService.storeVideo(video, "posts");

        assertTrue(storedPath.startsWith("/uploads/posts/"));
        assertTrue(storedPath.endsWith(".mp4"));
        assertTrue(java.nio.file.Files.exists(
                uploadDirectory.resolve(storedPath.substring("/uploads/".length()).replace('/', java.io.File.separatorChar))));

        FileStorageService.StoredFile segment = storageService.loadRange(storedPath, 4, 7);
        assertEquals(12, segment.totalSize());
        assertArrayEquals(new byte[] {'f', 't', 'y', 'p'}, segment.content());
    }

    @Test
    void storesValidatedWebmVideo() throws IOException {
        FileStorageService storageService = new FileStorageService(uploadDirectory.toString(), 5 * 1024 * 1024);
        byte[] webmHeader = new byte[] {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3};
        MockMultipartFile video = new MockMultipartFile("video", "journey.webm", "video/webm", webmHeader);

        String storedPath = storageService.storeVideo(video, "posts");

        assertTrue(storedPath.endsWith(".webm"));
    }

    @Test
    void acceptsMobileVideoWithGenericMimeTypeWhenExtensionAndContentMatch() throws IOException {
        FileStorageService storageService = new FileStorageService(uploadDirectory.toString(), 5 * 1024 * 1024);
        byte[] mp4Header = new byte[] {
                0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'
        };
        MockMultipartFile video = new MockMultipartFile(
                "video", "phone-recording.mp4", "application/octet-stream", mp4Header);

        String storedPath = storageService.storeVideo(video, "posts");

        assertTrue(storedPath.endsWith(".mp4"));
    }

    @Test
    void rejectsVideoWhoseContentDoesNotMatchDeclaredType() throws IOException {
        FileStorageService storageService = new FileStorageService(uploadDirectory.toString(), 5 * 1024 * 1024);
        MockMultipartFile fakeVideo = new MockMultipartFile(
                "video", "journey.mp4", "video/mp4",
                "this is not a video".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(IOException.class, () -> storageService.storeVideo(fakeVideo, "posts"));
    }
}
