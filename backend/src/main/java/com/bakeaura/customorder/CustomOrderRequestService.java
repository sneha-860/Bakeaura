package com.bakeaura.customorder;

import com.bakeaura.enums.CustomOrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomOrderRequestService {

    private final CustomOrderRequestRepository customOrderRequestRepository;

    @Transactional
    public CustomOrderRequest submitRequest(
            Long customerId,
            Long sellerId,
            String designBrief,
            String occasion,
            Integer serves,
            BigDecimal budgetMin,
            BigDecimal budgetMax) {

        if (customOrderRequestRepository.existsByCustomerIdAndSellerIdAndStatus(
                customerId, sellerId, CustomOrderStatus.PENDING)) {
            throw new IllegalStateException(
                    "You already have a pending request with this seller");
        }

        CustomOrderRequest request = new CustomOrderRequest();
        request.setCustomerId(customerId);
        request.setSellerId(sellerId);
        request.setDesignBrief(designBrief);
        request.setOccasion(occasion);
        request.setServes(serves);
        request.setBudgetMin(budgetMin);
        request.setBudgetMax(budgetMax);

        return customOrderRequestRepository.save(request);
    }

    @Transactional
    public CustomOrderRequest acceptRequest(Long requestId, Long sellerId) {
        CustomOrderRequest request = findAndValidateSeller(requestId, sellerId);

        if (request.getStatus() != CustomOrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be accepted");
        }

        request.setStatus(CustomOrderStatus.ACCEPTED);
        return customOrderRequestRepository.save(request);
    }

    @Transactional
    public CustomOrderRequest rejectRequest(Long requestId, Long sellerId) {
        CustomOrderRequest request = findAndValidateSeller(requestId, sellerId);

        if (request.getStatus() != CustomOrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be rejected");
        }

        request.setStatus(CustomOrderStatus.REJECTED);
        return customOrderRequestRepository.save(request);
    }

    @Transactional
    public CustomOrderRequest sendQuote(Long requestId, Long sellerId, BigDecimal quote) {
        CustomOrderRequest request = findAndValidateSeller(requestId, sellerId);

        if (request.getStatus() != CustomOrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be quoted");
        }

        request.setStatus(CustomOrderStatus.QUOTED);
        request.setSellerQuote(quote);
        return customOrderRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<CustomOrderRequest> getRequestsForCustomer(Long customerId) {
        return customOrderRequestRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @Transactional(readOnly = true)
    public List<CustomOrderRequest> getPendingRequestsForSeller(Long sellerId) {
        return customOrderRequestRepository.findBySellerIdAndStatusOrderByCreatedAtAsc(
                sellerId, CustomOrderStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<CustomOrderRequest> getAllRequestsForSeller(Long sellerId) {
        return customOrderRequestRepository.findBySellerIdOrderByCreatedAtDesc(sellerId);
    }

    private CustomOrderRequest findAndValidateSeller(Long requestId, Long sellerId) {
        CustomOrderRequest request = customOrderRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Custom order request not found: " + requestId));

        if (!request.getSellerId().equals(sellerId)) {
            throw new IllegalStateException(
                    "You are not authorised to respond to this request");
        }

        return request;
    }
}
