package com.bakeaura.payout;

import com.bakeaura.enums.PayoutStatus;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.notification.NotificationService;
import com.bakeaura.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayoutRequestService {

    private final PayoutRequestRepository payoutRequestRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;

    @Transactional
    public PayoutRequest submitRequest(Long influencerId, BigDecimal amount, String upiId) {
        if (payoutRequestRepository.existsByInfluencerIdAndStatus(influencerId, PayoutStatus.PENDING)) {
            throw new BadRequestException("You already have a pending payout request");
        }

        BigDecimal balance = walletService.getBalance(influencerId);
        if (balance.compareTo(amount) < 0) {
            throw new BadRequestException("Insufficient wallet balance. Available: " + balance);
        }

        PayoutRequest request = new PayoutRequest();
        request.setInfluencerId(influencerId);
        request.setAmount(amount);
        request.setUpiId(upiId);

        return payoutRequestRepository.save(request);
    }

    @Transactional
    public PayoutRequest approveRequest(Long requestId, Long adminId) {
        PayoutRequest request = payoutRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payout request not found: " + requestId));

        if (request.getStatus() != PayoutStatus.PENDING) {
            throw new BadRequestException("Only PENDING requests can be approved");
        }

        walletService.debit(
                request.getInfluencerId(),
                request.getAmount(),
                "Payout approved for UPI: " + request.getUpiId()
        );

        request.setStatus(PayoutStatus.APPROVED);
        request.setProcessedBy(adminId);
        request.setProcessedAt(LocalDateTime.now());

        PayoutRequest saved = payoutRequestRepository.save(request);

        notificationService.notifyUser(
                request.getInfluencerId(),
                "PAYOUT_APPROVED",
                "Your payout request of ₹" + request.getAmount() + " has been approved and will be transferred to your UPI shortly.",
                null
        );

        return saved;
    }

    @Transactional
    public PayoutRequest rejectRequest(Long requestId, Long adminId, String note) {
        PayoutRequest request = payoutRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payout request not found: " + requestId));

        if (request.getStatus() != PayoutStatus.PENDING) {
            throw new BadRequestException("Only PENDING requests can be rejected");
        }

        request.setStatus(PayoutStatus.REJECTED);
        request.setProcessedBy(adminId);
        request.setProcessedAt(LocalDateTime.now());
        request.setAdminNote(note);

        PayoutRequest saved = payoutRequestRepository.save(request);

        notificationService.notifyUser(
                request.getInfluencerId(),
                "PAYOUT_REJECTED",
                "Your payout request of ₹" + request.getAmount() + " was not approved." +
                        (note != null && !note.isBlank() ? " Reason: " + note : ""),
                null
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public List<PayoutRequest> getHistoryForInfluencer(Long influencerId) {
        return payoutRequestRepository.findByInfluencerIdOrderByCreatedAtDesc(influencerId);
    }

    @Transactional(readOnly = true)
    public List<PayoutRequest> getPendingRequests() {
        return payoutRequestRepository.findByStatusOrderByCreatedAtAsc(PayoutStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<PayoutRequest> getApprovedRequests() {
        return payoutRequestRepository.findByStatusOrderByCreatedAtAsc(PayoutStatus.APPROVED);
    }

    @Transactional
    public PayoutRequest markAsPaid(Long requestId, Long adminId) {
        PayoutRequest request = payoutRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payout request not found: " + requestId));

        if (request.getStatus() != PayoutStatus.APPROVED) {
            throw new BadRequestException("Only APPROVED requests can be marked as paid");
        }

        request.setStatus(PayoutStatus.PAID);
        request.setProcessedBy(adminId);
        request.setProcessedAt(LocalDateTime.now());

        PayoutRequest saved = payoutRequestRepository.save(request);

        notificationService.notifyUser(
                request.getInfluencerId(),
                "PAYOUT_PAID",
                "₹" + request.getAmount() + " has been transferred to your UPI account.",
                null
        );

        return saved;
    }
}