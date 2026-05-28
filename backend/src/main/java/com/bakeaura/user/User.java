package com.bakeaura.user;

import com.bakeaura.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data              // Lombok: auto-generates getters, setters, toString
@NoArgsConstructor // Lombok: generates empty constructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // IDENTITY means PostgreSQL auto-increments the ID (1, 2, 3...)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;
    // unique = true means two users can't have the same email

    @Column(nullable = false)
    private String password;
    // We'll store bcrypt hash, never plain text

    @Enumerated(EnumType.STRING)
    // Store role as "CUSTOMER" string, not a number
    private Role role;

    @Column(name = "is_active")
    private Boolean isActive = true;

    private Double latitude;

    private Double longitude;

    @CreationTimestamp
    private LocalDateTime createdAt;
    // Auto-set when record is created

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
