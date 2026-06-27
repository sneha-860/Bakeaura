package com.bakeaura.order;

import com.bakeaura.common.ApiResponse;
import com.bakeaura.enums.OrderStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
                .body(ApiResponse.ok("Order created",
                        orderService.createOrder(request, authentication.getName())));
    }

    @PostMapping("/from-cart")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<OrderResponseDto>> createOrderFromCart(
            @Valid @RequestBody CreateOrderFromCartRequestDto request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Order created",
                        orderService.createOrderFromCart(request, authentication.getName())));
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponseDto>> updateStatus(
            @PathVariable Long orderId,
            @RequestParam OrderStatus status,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Order status updated",
                orderService.updateStatus(orderId, status, authentication.getName())));
    }

    @GetMapping("/my-orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Page<OrderResponseDto>>> getMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Orders fetched",
                orderService.getMyOrders(authentication.getName(), page, size)));
    }

    @GetMapping("/seller-orders")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<Page<OrderResponseDto>>> getSellerOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Seller orders fetched",
                orderService.getSellerOrders(authentication.getName(), status, page, size)));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponseDto>> getOrderById(
            @PathVariable Long orderId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Order fetched",
                orderService.getOrderById(orderId, authentication.getName())));
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<OrderResponseDto>> cancelOrder(
            @PathVariable Long orderId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Order cancelled",
                orderService.cancelOrder(orderId, authentication.getName())));
    }
}
