package com.bakeaura.reel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReelRepository extends JpaRepository<Reel, Long> {

    // JOIN FETCH loads the seller in the same query so seller.getName() works
    // after the JPA session closes (required by the @Async processVideoUpload thread).
    @Query("SELECT r FROM Reel r JOIN FETCH r.seller WHERE r.id = :id")
    Optional<Reel> findByIdWithSeller(@Param("id") Long id);

    Page<Reel> findByStatusOrderByCreatedAtDesc(Reel.ReelStatus status, Pageable pageable);

    List<Reel> findBySeller_IdAndStatusOrderByCreatedAtDesc(Long sellerId, Reel.ReelStatus status);

    @Query("SELECT r FROM Reel r WHERE r.status = com.bakeaura.reel.Reel.ReelStatus.ACTIVE ORDER BY r.createdAt DESC")
    List<Reel> findAllActive(Pageable pageable);

    @Query("SELECT r FROM Reel r WHERE r.status = com.bakeaura.reel.Reel.ReelStatus.ACTIVE AND r.seller.id = :sellerId ORDER BY r.createdAt DESC")
    List<Reel> findActiveBySellerIdWithLimit(@Param("sellerId") Long sellerId, Pageable pageable);

    @Modifying
    @Query("UPDATE Reel r SET r.viewCount = r.viewCount + 1 WHERE r.id = :id")
    void incrementViewCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Reel r SET r.likeCount = r.likeCount + 1 WHERE r.id = :id")
    void incrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Reel r SET r.likeCount = r.likeCount - 1 WHERE r.id = :id AND r.likeCount > 0")
    void decrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Reel r SET r.saveCount = r.saveCount + 1 WHERE r.id = :id")
    void incrementSaveCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Reel r SET r.saveCount = r.saveCount - 1 WHERE r.id = :id AND r.saveCount > 0")
    void decrementSaveCount(@Param("id") Long id);
}
