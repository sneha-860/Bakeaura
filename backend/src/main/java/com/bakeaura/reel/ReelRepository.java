package com.bakeaura.reel;

import com.bakeaura.reel.Reel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReelRepository extends JpaRepository<Reel, Long> {

    List<Reel> findByStatusOrderByCreatedAtDesc(Reel.ReelStatus status);

    List<Reel> findBySellerIdAndStatusOrderByCreatedAtDesc(Long sellerId, Reel.ReelStatus status);
}
