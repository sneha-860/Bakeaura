package com.bakeaura.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProductDto {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
    private String imageUrl;
    private Boolean isAvailable;
    private Long sellerId;
    private String sellerName;
    private Long categoryId;
    private String categoryName;
    private LocalDateTime createdAt;
}
