package com.bakeaura.ai;

import com.bakeaura.customorder.CustomOrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/cake-design")
@RequiredArgsConstructor
public class CakeDesignAssistantController {

    private final CakeDesignAssistantService cakeDesignAssistantService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CakeDesignChatResponseDto> generateCustomOrder(
            @RequestBody CakeDesignChatRequestDto dto,
            Authentication authentication) {

        Long customerId = Long.parseLong(authentication.getName());

        CustomOrderRequest result = cakeDesignAssistantService.createCustomOrderFromDescription(
                customerId,
                dto.getSellerId(),
                dto.getDescription(),
                dto.getOccasion(),
                dto.getServes(),
                dto.getBudgetMin(),
                dto.getBudgetMax());

        CakeDesignChatResponseDto response = new CakeDesignChatResponseDto(
                result.getId(),
                result.getDesignBrief(),
                result.getGeneratedImageUrl(),
                result.getStatus().name());

        return ResponseEntity.ok(response);
    }
}
