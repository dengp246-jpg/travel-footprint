package com.example.travelfootprint.service;

import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.repository.TravelPostRepository;
import java.io.IOException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PostVideoService {

    private final TravelPostRepository postRepository;
    private final FileStorageService fileStorageService;

    public PostVideoService(TravelPostRepository postRepository, FileStorageService fileStorageService) {
        this.postRepository = postRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public void updateVideo(TravelPost post, MultipartFile upload, boolean removeExisting) throws IOException {
        String previousPath = post.getVideoPath();
        if (upload != null && !upload.isEmpty()) {
            String newPath = fileStorageService.storeVideo(upload, "posts");
            post.setVideoPath(newPath);
            postRepository.save(post);
            deleteQuietly(previousPath);
            return;
        }
        if (removeExisting && previousPath != null && !previousPath.isBlank()) {
            post.setVideoPath(null);
            postRepository.save(post);
            deleteQuietly(previousPath);
        }
    }

    public void validateUpload(MultipartFile upload) throws IOException {
        fileStorageService.validateVideo(upload);
    }

    @Transactional
    public void deleteVideo(TravelPost post) {
        if (post == null || post.getVideoPath() == null || post.getVideoPath().isBlank()) {
            return;
        }
        String path = post.getVideoPath();
        post.setVideoPath(null);
        postRepository.save(post);
        deleteQuietly(path);
    }

    private void deleteQuietly(String publicPath) {
        if (publicPath == null || publicPath.isBlank()) {
            return;
        }
        try {
            fileStorageService.delete(publicPath);
        } catch (IOException ignored) {
            // A stale file is safer than failing the user's post update after the database has changed.
        }
    }
}
