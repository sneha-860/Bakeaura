package com.bakeaura.review;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ReviewDto {
    private Long id;
    private Long sellerId;
    // customerId intentionally omitted — public endpoint must not expose PII
    private String customerName;   // anonymised to "First L." in ReviewService.toDto()
    private Long orderId;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}
