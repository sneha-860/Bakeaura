package com.bakeaura.payout;

import com.bakeaura.enums.PayoutStatus;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.notification.NotificationService;
import com.bakeaura.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public PayoutRequestDto submitRequest(Long influencerId, BigDecimal amount, String upiId) {
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

        return toDto(payoutRequestRepository.save(request));
    }

    // APPROVE: status only — no wallet debit yet. Money leaves the wallet at PAID stage
    // when the admin confirms the bank transfer has actually happened.
    @Transactional
    public PayoutRequestDto approveRequest(Long requestId, Long adminId) {
        PayoutRequest request = payoutRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout request not found: " + requestId));

        if (request.getStatus() != PayoutStatus.PENDING) {
            throw new BadRequestException("Only PENDING requests can be approved");
        }

        request.setStatus(PayoutStatus.APPROVED);
        request.setProcessedBy(adminId);
        request.setProcessedAt(LocalDateTime.now());

        PayoutRequestDto dto = toDto(payoutRequestRepository.save(request));

        notificationService.notifyUser(
                request.getInfluencerId(),
                "PAYOUT_APPROVED",
                "Your payout request of ₹" + request.getAmount() + " has been approved. Transfer is being processed.",
                request.getId()
        );

        return dto;
    }

    // REJECT: allowed from both PENDING and APPROVED. Since DEBIT only happens at PAID,
    // rejecting an APPROVED request has no wallet side-effect — just a status change.
    @Transactional
    public PayoutRequestDto rejectRequest(Long requestId, Long adminId, String note) {
        PayoutRequest request = payoutRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout request not found: " + requestId));

        if (request.getStatus() != PayoutStatus.PENDING && request.getStatus() != PayoutStatus.APPROVED) {
            throw new BadRequestException("Only PENDING or APPROVED requests can be rejected");
        }

        request.setStatus(PayoutStatus.REJECTED);
        request.setProcessedBy(adminId);
        request.setProcessedAt(LocalDateTime.now());
        request.setAdminNote(note);

        PayoutRequestDto dto = toDto(payoutRequestRepository.save(request));

        notificationService.notifyUser(
                request.getInfluencerId(),
                "PAYOUT_REJECTED",
                "Your payout request of ₹" + request.getAmount() + " was not approved." +
                        (note != null && !note.isBlank() ? " Reason: " + note : ""),
                request.getId()
        );

        return dto;
    }

    // PAID: this is the only place the wallet is debited. The transfer has happened in the
    // real world; now we record that the money has left the platform wallet.
    @Transactional
    public PayoutRequestDto markAsPaid(Long requestId, Long adminId) {
        PayoutRequest request = payoutRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout request not found: " + requestId));

        if (request.getStatus() != PayoutStatus.APPROVED) {
            throw new BadRequestException("Only APPROVED requests can be marked as paid");
        }

        try {
            walletService.debit(
                    request.getInfluencerId(),
                    request.getAmount(),
                    "Payout disbursed to UPI: " + request.getUpiId()
            );
        } catch (IllegalStateException e) {
            throw new BadRequestException("Cannot process payout: " + e.getMessage());
        } catch (DataAccessException e) {
            throw new BadRequestException("Payout could not be processed due to a concurrent transaction — please retry");
        }

        request.setStatus(PayoutStatus.PAID);
        request.setProcessedBy(adminId);
        request.setProcessedAt(LocalDateTime.now());

        PayoutRequestDto dto = toDto(payoutRequestRepository.save(request));

        notificationService.notifyUser(
                request.getInfluencerId(),
                "PAYOUT_PAID",
                "₹" + request.getAmount() + " has been transferred to your UPI account.",
                request.getId()
        );

        return dto;
    }

    @Transactional(readOnly = true)
    public List<PayoutRequestDto> getHistoryForInfluencer(Long influencerId) {
        return payoutRequestRepository.findByInfluencerIdOrderByCreatedAtDesc(influencerId)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<PayoutRequestDto> getPendingRequests() {
        return payoutRequestRepository.findByStatusOrderByCreatedAtAsc(PayoutStatus.PENDING)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<PayoutRequestDto> getApprovedRequests() {
        return payoutRequestRepository.findByStatusOrderByCreatedAtAsc(PayoutStatus.APPROVED)
                .stream().map(this::toDto).toList();
    }

    private PayoutRequestDto toDto(PayoutRequest p) {
        return new PayoutRequestDto(
                p.getId(),
                p.getInfluencerId(),
                p.getAmount(),
                p.getStatus().name(),
                p.getUpiId(),
                p.getAdminNote(),
                p.getProcessedAt(),
                p.getCreatedAt()
        );
    }
}
