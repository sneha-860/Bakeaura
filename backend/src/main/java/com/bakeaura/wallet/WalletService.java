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

    // SERIALIZABLE isolation makes the balance-read and DEBIT-insert atomic under PostgreSQL SSI.
    // Two concurrent payout approvals for the same influencer will serialize: one commits,
    // the other gets a CannotSerializeTransactionException which Spring propagates as a 500,
    // so the admin must retry. This is safer than a race that allows double-debit.
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void debit(Long influencerId, BigDecimal amount, String description) {
        BigDecimal currentBalance = walletTransactionRepository.calculateBalance(influencerId, WalletTransactionType.CREDIT, WalletTransactionType.DEBIT);
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
        return walletTransactionRepository.calculateBalance(influencerId, WalletTransactionType.CREDIT, WalletTransactionType.DEBIT);
    }

    @Transactional(readOnly = true)
    public List<WalletTransactionDto> getTransactionHistory(Long influencerId) {
        return walletTransactionRepository.findByInfluencerIdOrderByCreatedAtDesc(influencerId)
                .stream()
                .map(t -> new WalletTransactionDto(
                        t.getId(),
                        t.getInfluencerId(),
                        t.getAmount(),
                        t.getType().name(),
                        t.getDescription(),
                        t.getCreatedAt()
                ))
                .toList();
    }
}
