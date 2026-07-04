package com.bakeaura.reel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReelRepository extends JpaRepository<Reel, Long> {

    List<Reel> findByStatusOrderByCreatedAtDesc(Reel.ReelStatus status);

    Page<Reel> findByStatusOrderByCreatedAtDesc(Reel.ReelStatus status, Pageable pageable);

    List<Reel> findBySeller_IdAndStatusOrderByCreatedAtDesc(Long sellerId, Reel.ReelStatus status);

    @Query("SELECT r FROM Reel r WHERE r.status = com.bakeaura.reel.Reel.ReelStatus.ACTIVE")
    List<Reel> findAllActive();

    @Modifying
    @Query("UPDATE Reel r SET r.viewCount = r.viewCount + 1 WHERE r.id = :id")
    void incrementViewCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Reel r SET r.likeCount = r.likeCount + 1 WHERE r.id = :id")
    void incrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Reel r SET r.saveCount = r.saveCount + 1 WHERE r.id = :id")
    void incrementSaveCount(@Param("id") Long id);
}
