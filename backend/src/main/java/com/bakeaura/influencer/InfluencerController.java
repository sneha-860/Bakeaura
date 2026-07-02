package com.bakeaura.influencer;

import com.bakeaura.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/influencers")
@RequiredArgsConstructor
public class InfluencerController {

    private final InfluencerProfileService influencerProfileService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<InfluencerPublicDto>>> getInfluencers() {
        return ResponseEntity.ok(ApiResponse.ok("Influencers fetched",
                influencerProfileService.getInfluencers()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InfluencerPublicDto>> getInfluencer(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Influencer fetched",
                influencerProfileService.getInfluencer(id)));
    }
}
