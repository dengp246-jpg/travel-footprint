package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.TravelPostPhoto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelPostPhotoRepository extends JpaRepository<TravelPostPhoto, Long> {

    List<TravelPostPhoto> findByPostIdOrderBySortOrderAscIdAsc(Long postId);

    long countByPostId(Long postId);

    void deleteByPostId(Long postId);

    Optional<TravelPostPhoto> findFirstByPhotoPath(String photoPath);
}
