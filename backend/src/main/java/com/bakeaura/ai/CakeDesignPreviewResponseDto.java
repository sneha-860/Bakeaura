package com.bakeaura.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CakeDesignPreviewResponseDto {
    private String designBrief;
    private String imageBase64;
    private String mimeType;
}
