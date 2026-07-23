package com.bakeaura.wallet;

import com.bakeaura.enums.WalletTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    List<WalletTransaction> findByInfluencerIdOrderByCreatedAtDesc(Long influencerId);

    @Query("SELECT COALESCE(" +
            "SUM(CASE WHEN w.type = :credit THEN w.amount ELSE 0 END) - " +
            "SUM(CASE WHEN w.type = :debit THEN w.amount ELSE 0 END), " +
            "0) FROM WalletTransaction w WHERE w.influencerId = :influencerId")
    BigDecimal calculateBalance(@Param("influencerId") Long influencerId,
                                @Param("credit") WalletTransactionType credit,
                                @Param("debit") WalletTransactionType debit);
}
