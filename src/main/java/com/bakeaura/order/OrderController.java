package com.bakeaura.order;


import com.bakeaura.common.ApiResponse;
import com.bakeaura.enums.OrderStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<OrderResponseDto>> createOrder(
            @Valid @RequestBody CreateOrderRequestDto request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Order created", orderService.createOrder(request, authentication.getName())));
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponseDto>> updateStatus(
            @PathVariable Long orderId,
            @RequestParam OrderStatus status,
            Authentication authentication) {
        return ResponseEntity.ok(
                ApiResponse.ok("Order status updated", orderService.updateStatus(orderId, status, authentication.getName())));
    }

    @GetMapping("/my-orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<List<OrderResponseDto>>> getMyOrders(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Orders fetched", orderService.getMyOrders(authentication.getName())));
    }

    @GetMapping("/seller-orders")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<List<OrderResponseDto>>> getSellerOrders(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Seller orders fetched", orderService.getSellerOrders(authentication.getName())));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponseDto>> getOrderById(
            @PathVariable Long orderId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Order fetched", orderService.getOrderById(orderId, authentication.getName())));
    }
}
