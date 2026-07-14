package com.bakeaura.roleapplication;

import com.bakeaura.enums.ApplicationStatus;
import com.bakeaura.enums.Role;
import com.bakeaura.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "role_applications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role requestedRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(length = 2000)
    private String message;

    // Phone submitted at application time — copied to User.phone only on approval
    private String phone;

    // For INFLUENCER applications — admin needs these to verify the applicant
    private String socialUrl;

    private Integer followerCount;

    @Column(length = 1000)
    private String reviewNote;

    private String reviewedBy;

    private LocalDateTime reviewedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
