package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.StoredImage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredImageRepository extends JpaRepository<StoredImage, Long> {

    Optional<StoredImage> findByPublicPath(String publicPath);

    void deleteByPublicPath(String publicPath);
}
