//AddressController exposes REST APIs for managing user addresses. It receives HTTP requests, extracts request data and authenticated user information, delegates business logic to AddressService, and returns standardized API responses using ApiResponse and ResponseEntity. It does not contain business logic itself.


package com.bakeaura.address;

import com.bakeaura.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {
    private final AddressService addressService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressDto>>> getAddresses(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Addresses fetched", addressService.getAddresses(authentication.getName())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AddressDto>> createAddress(
            Authentication authentication,
            @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.ok("Address created", addressService.createAddress(authentication.getName(), request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressDto>> updateAddress(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Address updated", addressService.updateAddress(authentication.getName(), id, request)));
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<ApiResponse<AddressDto>> setDefault(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Default address updated", addressService.setDefault(authentication.getName(), id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(Authentication authentication, @PathVariable Long id) {
        addressService.deleteAddress(authentication.getName(), id);
        return ResponseEntity.ok(ApiResponse.ok("Address deleted", null));
    }
}
