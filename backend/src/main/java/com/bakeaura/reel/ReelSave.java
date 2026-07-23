package com.bakeaura.reel;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "reel_saves",
        uniqueConstraints = @UniqueConstraint(columnNames = {"reel_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ReelSave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reel_id", nullable = false)
    private Long reelId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
