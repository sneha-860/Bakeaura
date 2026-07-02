package com.bakeaura.wallet;

import com.bakeaura.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/influencer/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/balance")
    @PreAuthorize("hasRole('INFLUENCER')")
    public ResponseEntity<ApiResponse<BigDecimal>> getBalance(Authentication authentication) {
        Long influencerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Balance fetched",
                walletService.getBalance(influencerId)));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasRole('INFLUENCER')")
    public ResponseEntity<ApiResponse<List<WalletTransaction>>> getTransactions(
            Authentication authentication) {
        Long influencerId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Transactions fetched",
                walletService.getTransactionHistory(influencerId)));
    }
}
