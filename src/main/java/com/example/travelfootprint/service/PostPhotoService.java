package com.example.travelfootprint.service;

import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.TravelPostPhoto;
import com.example.travelfootprint.repository.TravelPostPhotoRepository;
import com.example.travelfootprint.repository.TravelPostRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PostPhotoService {

    public static final int MAX_PHOTOS_PER_POST = 9;

    private final TravelPostPhotoRepository photoRepository;
    private final TravelPostRepository postRepository;
    private final FileStorageService fileStorageService;

    public PostPhotoService(
            TravelPostPhotoRepository photoRepository,
            TravelPostRepository postRepository,
            FileStorageService fileStorageService) {
        this.photoRepository = photoRepository;
        this.postRepository = postRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public List<PhotoView> gallery(TravelPost post) {
        List<PhotoView> photos = photoRepository.findByPostIdOrderBySortOrderAscIdAsc(post.getId()).stream()
                .map(photo -> new PhotoView(photo.getId(), photo.getPhotoPath(), photo.isCover(), photo.getSortOrder()))
                .toList();
        if (!photos.isEmpty()) {
            return photos;
        }
        if (post.getPhotoPath() == null || post.getPhotoPath().isBlank()) {
            return List.of();
        }
        return List.of(new PhotoView(null, post.getPhotoPath(), true, 0));
    }

    @Transactional
    public void addPhotos(TravelPost post, List<MultipartFile> uploads, Integer coverIndex) throws IOException {
        List<MultipartFile> selected = uploads == null ? List.of() : uploads.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
        if (selected.isEmpty()) {
            return;
        }
        long existingCount = photoRepository.countByPostId(post.getId());
        if (existingCount + selected.size() > MAX_PHOTOS_PER_POST) {
            throw new IOException("每篇足迹最多保存 " + MAX_PHOTOS_PER_POST + " 张照片。");
        }

        List<String> storedPaths = new ArrayList<>();
        for (MultipartFile upload : selected) {
            storedPaths.add(fileStorageService.store(upload, "posts"));
        }

        int selectedCover = coverIndex == null ? 0 : Math.max(0, Math.min(coverIndex, storedPaths.size() - 1));
        boolean replaceCover = existingCount == 0 || coverIndex != null;
        if (replaceCover) {
            List<TravelPostPhoto> existingPhotos = photoRepository.findByPostIdOrderBySortOrderAscIdAsc(post.getId());
            existingPhotos.forEach(photo -> photo.setCover(false));
            photoRepository.saveAll(existingPhotos);
        }

        List<TravelPostPhoto> newPhotos = new ArrayList<>();
        for (int index = 0; index < storedPaths.size(); index++) {
            TravelPostPhoto photo = new TravelPostPhoto();
            photo.setPost(post);
            photo.setPhotoPath(storedPaths.get(index));
            photo.setSortOrder((int) existingCount + index);
            photo.setCover(replaceCover && index == selectedCover);
            newPhotos.add(photo);
        }
        photoRepository.saveAll(newPhotos);
        if (replaceCover) {
            post.setPhotoPath(storedPaths.get(selectedCover));
            postRepository.save(post);
        } else if (post.getPhotoPath() == null || post.getPhotoPath().isBlank()) {
            post.setPhotoPath(storedPaths.get(0));
            postRepository.save(post);
        }
    }

    @Transactional
    public boolean setCover(TravelPost post, Long photoId) {
        TravelPostPhoto selected = photoRepository.findById(photoId).orElse(null);
        if (selected == null || !selected.getPost().getId().equals(post.getId())) {
            return false;
        }
        List<TravelPostPhoto> photos = photoRepository.findByPostIdOrderBySortOrderAscIdAsc(post.getId());
        photos.forEach(photo -> photo.setCover(photo.getId().equals(photoId)));
        photoRepository.saveAll(photos);
        post.setPhotoPath(selected.getPhotoPath());
        postRepository.save(post);
        return true;
    }

    @Transactional
    public void deleteByPostId(Long postId) {
        photoRepository.deleteByPostId(postId);
    }

    public record PhotoView(Long id, String path, boolean cover, int sortOrder) {
    }
}
