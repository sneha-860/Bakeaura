package com.bakeaura.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CakeDesignChatResponseDto {
    private Long requestId;
    private String designBrief;
    private String generatedImageUrl;
    private String status;
}
