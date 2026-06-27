package com.bakeaura.content;

import com.bakeaura.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<List<FeedItem>>> getFeed() {
        List<FeedItem> feed = contentService.getRankedFeed();
        return ResponseEntity.ok(ApiResponse.ok("Feed fetched successfully", feed));
    }
}