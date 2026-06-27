package com.bakeaura.influencer;

import com.bakeaura.enums.CollaborationStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CollaborationResponse {

    private Long id;
    private Long influencerId;
    private Long sellerId;
    private CollaborationStatus status;
    private String message;
    private LocalDateTime createdAt;
}
