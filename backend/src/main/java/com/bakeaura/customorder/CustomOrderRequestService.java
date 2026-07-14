package com.bakeaura.customorder;

import com.bakeaura.enums.CustomOrderStatus;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.notification.NotificationService;
import com.bakeaura.user.User;
import com.bakeaura.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomOrderRequestService {

    private final CustomOrderRequestRepository customOrderRequestRepository;
    private final UserService userService;
    private final NotificationService notificationService;

    @Transactional
    public CustomOrderResponseDto submitRequest(
            Long customerId,
            Long sellerId,
            String designBrief,
            String occasion,
            Integer serves,
            BigDecimal budgetMin,
            BigDecimal budgetMax) {

        User customer = userService.getById(customerId);

        if (!Boolean.TRUE.equals(customer.getIsEmailVerified())) {
            throw new BadRequestException("Please verify your email address before submitting a custom order request");
        }

        if (!userService.isActiveSeller(sellerId)) {
            throw new BadRequestException("Selected seller is not a valid active seller");
        }

        if (customOrderRequestRepository.existsByCustomerIdAndSellerIdAndStatus(
                customerId, sellerId, CustomOrderStatus.PENDING)) {
            throw new BadRequestException("You already have a pending request with this seller");
        }

        CustomOrderRequest request = new CustomOrderRequest();
        request.setCustomerId(customerId);
        request.setSellerId(sellerId);
        request.setDesignBrief(designBrief);
        request.setOccasion(occasion);
        request.setServes(serves);
        request.setBudgetMin(budgetMin);
        request.setBudgetMax(budgetMax);

        CustomOrderRequest saved = customOrderRequestRepository.save(request);

        String customerName = userService.getUserName(customerId);
        notificationService.notifyUser(
                sellerId,
                "CUSTOM_ORDER_REQUEST",
                customerName + " has sent you a custom cake request.",
                saved.getId()
        );

        return toDto(saved);
    }

    @Transactional
    public CustomOrderResponseDto submitAiGeneratedRequest(
            Long customerId,
            Long sellerId,
            String designBrief,
            String generatedImageUrl,
            String occasion,
            Integer serves,
            BigDecimal budgetMin,
            BigDecimal budgetMax) {

        User aiCustomer = userService.getById(customerId);

        if (!Boolean.TRUE.equals(aiCustomer.getIsEmailVerified())) {
            throw new BadRequestException("Please verify your email address before submitting a custom order request");
        }

        if (!userService.isActiveSeller(sellerId)) {
            throw new BadRequestException("Selected seller is not a valid active seller");
        }

        if (customOrderRequestRepository.existsByCustomerIdAndSellerIdAndStatus(
                customerId, sellerId, CustomOrderStatus.PENDING)) {
            throw new BadRequestException("You already have a pending request with this seller");
        }

        CustomOrderRequest request = new CustomOrderRequest();
        request.setCustomerId(customerId);
        request.setSellerId(sellerId);
        request.setDesignBrief(designBrief);
        request.setGeneratedImageUrl(generatedImageUrl);
        request.setOccasion(occasion);
        request.setServes(serves);
        request.setBudgetMin(budgetMin);
        request.setBudgetMax(budgetMax);

        CustomOrderRequest saved = customOrderRequestRepository.save(request);

        // Notify the seller — same as the manual path
        String customerName = userService.getUserName(customerId);
        notificationService.notifyUser(
                sellerId,
                "CUSTOM_ORDER_REQUEST",
                customerName + " has sent you an AI-designed custom cake request.",
                saved.getId()
        );

        return toDto(saved);
    }

    @Transactional
    public CustomOrderResponseDto acceptRequest(Long requestId, Long sellerId) {
        CustomOrderRequest request = findAndValidateSeller(requestId, sellerId);

        if (request.getStatus() != CustomOrderStatus.PENDING) {
            throw new BadRequestException("Only PENDING requests can be accepted");
        }

        request.setStatus(CustomOrderStatus.ACCEPTED);
        CustomOrderRequest saved = customOrderRequestRepository.save(request);

        String sellerName = userService.getUserName(sellerId);
        notificationService.notifyUser(
                request.getCustomerId(),
                "CUSTOM_ORDER_ACCEPTED",
                sellerName + " has accepted your custom cake request.",
                saved.getId()
        );

        return toDto(saved);
    }

    @Transactional
    public CustomOrderResponseDto rejectRequest(Long requestId, Long sellerId) {
        CustomOrderRequest request = findAndValidateSeller(requestId, sellerId);

        if (request.getStatus() != CustomOrderStatus.PENDING) {
            throw new BadRequestException("Only PENDING requests can be rejected");
        }

        request.setStatus(CustomOrderStatus.REJECTED);
        CustomOrderRequest saved = customOrderRequestRepository.save(request);

        String sellerName = userService.getUserName(sellerId);
        notificationService.notifyUser(
                request.getCustomerId(),
                "CUSTOM_ORDER_REJECTED",
                sellerName + " has declined your custom cake request.",
                saved.getId()
        );

        return toDto(saved);
    }

    @Transactional
    public CustomOrderResponseDto sendQuote(Long requestId, Long sellerId, BigDecimal quote) {
        CustomOrderRequest request = findAndValidateSeller(requestId, sellerId);

        // Allow re-quoting from QUOTED as well as initial quoting from PENDING
        if (request.getStatus() != CustomOrderStatus.PENDING
                && request.getStatus() != CustomOrderStatus.QUOTED) {
            throw new BadRequestException("Quotes can only be sent for PENDING or QUOTED requests");
        }

        request.setStatus(CustomOrderStatus.QUOTED);
        request.setSellerQuote(quote);
        CustomOrderRequest saved = customOrderRequestRepository.save(request);

        String sellerName = userService.getUserName(sellerId);
        notificationService.notifyUser(
                request.getCustomerId(),
                "CUSTOM_ORDER_QUOTED",
                sellerName + " sent you a quote of ₹" + quote + " for your custom cake.",
                saved.getId()
        );

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<CustomOrderResponseDto> getRequestsForCustomer(Long customerId) {
        return customOrderRequestRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<CustomOrderResponseDto> getPendingRequestsForSeller(Long sellerId) {
        return customOrderRequestRepository.findBySellerIdAndStatusOrderByCreatedAtAsc(
                sellerId, CustomOrderStatus.PENDING)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<CustomOrderResponseDto> getAllRequestsForSeller(Long sellerId) {
        return customOrderRequestRepository.findBySellerIdOrderByCreatedAtDesc(sellerId)
                .stream().map(this::toDto).toList();
    }

    private CustomOrderRequest findAndValidateSeller(Long requestId, Long sellerId) {
        CustomOrderRequest request = customOrderRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Custom order request not found: " + requestId));

        if (!request.getSellerId().equals(sellerId)) {
            throw new BadRequestException("You are not authorised to respond to this request");
        }

        return request;
    }

    private CustomOrderResponseDto toDto(CustomOrderRequest r) {
        return CustomOrderResponseDto.builder()
                .id(r.getId())
                .customerId(r.getCustomerId())
                .sellerId(r.getSellerId())
                .designBrief(r.getDesignBrief())
                .generatedImageUrl(r.getGeneratedImageUrl())
                .occasion(r.getOccasion())
                .serves(r.getServes())
                .budgetMin(r.getBudgetMin())
                .budgetMax(r.getBudgetMax())
                .status(r.getStatus())
                .sellerQuote(r.getSellerQuote())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
