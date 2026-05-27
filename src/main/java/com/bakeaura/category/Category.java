package com.bakeaura.category;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "categories")
@Data
@NoArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(unique = true, nullable = false, length = 100)
    private String name;
    // e.g. "Cakes", "Cookies", "Pastries", "Bread"

    @Size(max = 500)
    @Column(length = 500)
    private String description;

    @Size(max = 1000)
    @Column(length = 1000)
    private String imageUrl;
}
