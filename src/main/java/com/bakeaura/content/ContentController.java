package com.bakeaura.content;

import com.bakeaura.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/content")
public class ContentController {

    private final List<Map<String, Object>> mockFeed = new ArrayList<>();

    public ContentController() {
        // Populate mock data for feed
        // 1. Reel
        Map<String, Object> reel1 = new HashMap<>();
        reel1.put("id", 1L);
        reel1.put("type", "REEL");
        reel1.put("imageUrl", "https://images.unsplash.com/photo-1578985545062-69928b1d9587?w=500&auto=format&fit=crop&q=60");
        reel1.put("thumbnailUrl", "https://images.unsplash.com/photo-1578985545062-69928b1d9587?w=500&auto=format&fit=crop&q=60");
        reel1.put("caption", "Whisking up some double-chocolate fudge brownies today! 🍫✨");
        reel1.put("bakerName", "Sarah's Sweet Oven");
        reel1.put("likeCount", 342);
        reel1.put("commentCount", 45);
        reel1.put("sellerId", 2L);
        mockFeed.add(reel1);

        // 2. Post
        Map<String, Object> post1 = new HashMap<>();
        post1.put("id", 2L);
        post1.put("type", "POST");
        post1.put("imageUrl", "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=500&auto=format&fit=crop&q=60");
        post1.put("caption", "Freshly baked sourdough boules cooling down. The crust is absolutely perfect today! 🥖🌾");
        post1.put("bakerName", "Artisanal Crusts");
        post1.put("likeCount", 189);
        post1.put("commentCount", 12);
        post1.put("sellerId", 2L);
        mockFeed.add(post1);

        // 3. Review (Influencer)
        Map<String, Object> review1 = new HashMap<>();
        review1.put("id", 3L);
        review1.put("type", "REVIEW");
        review1.put("imageUrl", "https://images.unsplash.com/photo-1535141192574-5d4897c13636?w=500&auto=format&fit=crop&q=60");
        review1.put("caption", "Omg guys, I just tried the red velvet cupcakes from Sarah's Sweet Oven and they are heaven!");
        review1.put("bakerName", "Sarah's Sweet Oven");
        review1.put("likeCount", 856);
        review1.put("commentCount", 98);
        review1.put("sellerId", 2L);
        mockFeed.add(review1);

        // 4. Product
        Map<String, Object> prod1 = new HashMap<>();
        prod1.put("id", 4L);
        prod1.put("type", "PRODUCT");
        prod1.put("imageUrl", "https://images.unsplash.com/photo-1558961317-1f2988e515b1?w=500&auto=format&fit=crop&q=60");
        prod1.put("bakerName", "Sarah's Sweet Oven");
        prod1.put("likeCount", 112);
        prod1.put("commentCount", 8);
        prod1.put("sellerId", 2L);
        Map<String, Object> p1 = new HashMap<>();
        p1.put("name", "Classic Chocolate Chip Cookies");
        p1.put("price", 150);
        p1.put("description", "Chewy, buttery, chocolate-loaded goodness.");
        p1.put("imageUrl", "https://images.unsplash.com/photo-1558961317-1f2988e515b1?w=500&auto=format&fit=crop&q=60");
        prod1.put("product", p1);
        mockFeed.add(prod1);

        // 5. Reel 2
        Map<String, Object> reel2 = new HashMap<>();
        reel2.put("id", 5L);
        reel2.put("type", "REEL");
        reel2.put("imageUrl", "https://images.unsplash.com/photo-1621980244079-7157a3e75e54?w=500&auto=format&fit=crop&q=60");
        reel2.put("thumbnailUrl", "https://images.unsplash.com/photo-1621980244079-7157a3e75e54?w=500&auto=format&fit=crop&q=60");
        reel2.put("caption", "Icing compilation of my custom wedding cakes. So satisfying! 🎂✨");
        reel2.put("bakerName", "The Cake Studio");
        reel2.put("likeCount", 1205);
        reel2.put("commentCount", 143);
        reel2.put("sellerId", 2L);
        mockFeed.add(reel2);

        // 6. Post 2
        Map<String, Object> post2 = new HashMap<>();
        post2.put("id", 6L);
        post2.put("type", "POST");
        post2.put("imageUrl", "https://images.unsplash.com/photo-149514740007a-18a1833f4a7c?w=500&auto=format&fit=crop&q=60");
        post2.put("caption", "Sweet macarons filled with white chocolate and raspberry ganache! 💕");
        post2.put("bakerName", "The Cake Studio");
        post2.put("likeCount", 243);
        post2.put("commentCount", 19);
        post2.put("sellerId", 2L);
        mockFeed.add(post2);
    }

    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFeed(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {

        List<Map<String, Object>> filtered = mockFeed.stream()
                .filter(item -> type == null || type.isBlank() || type.equalsIgnoreCase((String) item.get("type")))
                .filter(item -> sellerId == null || sellerId.equals(item.get("sellerId")))
                .filter(item -> {
                    if (q == null || q.isBlank()) return true;
                    String text = ((String) item.getOrDefault("caption", "")) + " " + ((String) item.getOrDefault("bakerName", ""));
                    if (item.containsKey("product")) {
                        Map<String, Object> p = (Map<String, Object>) item.get("product");
                        text += " " + p.getOrDefault("name", "") + " " + p.getOrDefault("description", "");
                    }
                    return text.toLowerCase().contains(q.toLowerCase());
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.ok("Feed fetched successfully", filtered));
    }
}
