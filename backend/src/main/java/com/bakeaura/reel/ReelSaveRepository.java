package com.bakeaura.reel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ReelSaveRepository extends JpaRepository<ReelSave, Long> {
    boolean existsByReelIdAndUserId(Long reelId, Long userId);

    @Transactional
    void deleteByReelIdAndUserId(Long reelId, Long userId);

    @Transactional
    void deleteAllByReelId(Long reelId);
}
