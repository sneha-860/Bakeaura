package com.bakeaura.category;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CategoryResponseDto {
    private Long id;
    private String name;
    private String description;
    private String imageUrl;
}
