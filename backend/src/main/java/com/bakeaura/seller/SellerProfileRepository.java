package com.bakeaura.seller;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SellerProfileRepository extends JpaRepository<SellerProfile, Long> {

    Optional<SellerProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    List<SellerProfile> findAllByUserIdIn(Collection<Long> userIds);
}