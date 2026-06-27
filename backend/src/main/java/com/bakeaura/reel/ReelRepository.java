package com.bakeaura.reel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReelRepository extends JpaRepository<Reel, Long> {

    List<Reel> findByStatusOrderByCreatedAtDesc(Reel.ReelStatus status);

    Page<Reel> findByStatusOrderByCreatedAtDesc(Reel.ReelStatus status, Pageable pageable);

    List<Reel> findBySeller_IdAndStatusOrderByCreatedAtDesc(Long sellerId, Reel.ReelStatus status);

    @Query("SELECT r FROM Reel r WHERE r.status = com.bakeaura.reel.Reel.ReelStatus.ACTIVE")
    List<Reel> findBySeller_StatusActive();
}
