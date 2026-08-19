package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.TravelPostPhoto;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelPostPhotoRepository extends JpaRepository<TravelPostPhoto, Long> {

    List<TravelPostPhoto> findByPostIdOrderBySortOrderAscIdAsc(Long postId);

    long countByPostId(Long postId);

    @Query("select photo.post.id as postId, count(photo.id) as photoCount "
            + "from TravelPostPhoto photo where photo.post.id in :postIds group by photo.post.id")
    List<PostPhotoCount> countByPostIds(@Param("postIds") Collection<Long> postIds);

    void deleteByPostId(Long postId);

    Optional<TravelPostPhoto> findFirstByPhotoPath(String photoPath);

    interface PostPhotoCount {
        Long getPostId();

        long getPhotoCount();
    }
}
