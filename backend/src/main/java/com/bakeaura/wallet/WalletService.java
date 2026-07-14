package com.bakeaura.wallet;

import com.bakeaura.enums.WalletTransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletTransactionRepository walletTransactionRepository;

    @Transactional
    public void credit(Long influencerId, BigDecimal amount, String description) {
        WalletTransaction transaction = new WalletTransaction();
        transaction.setInfluencerId(influencerId);
        transaction.setAmount(amount);
        transaction.setType(WalletTransactionType.CREDIT);
        transaction.setDescription(description);
        walletTransactionRepository.save(transaction);
    }

    @Transactional
    public void debit(Long influencerId, BigDecimal amount, String description) {
        BigDecimal currentBalance = getBalance(influencerId);
        if (currentBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient wallet balance for influencer " + influencerId);
        }
        WalletTransaction transaction = new WalletTransaction();
        transaction.setInfluencerId(influencerId);
        transaction.setAmount(amount);
        transaction.setType(WalletTransactionType.DEBIT);
        transaction.setDescription(description);
        walletTransactionRepository.save(transaction);
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long influencerId) {
        return walletTransactionRepository.calculateBalance(influencerId, WalletTransactionType.CREDIT);
    }

    @Transactional(readOnly = true)
    public List<WalletTransaction> getTransactionHistory(Long influencerId) {
        return walletTransactionRepository.findByInfluencerIdOrderByCreatedAtDesc(influencerId);
    }
}
