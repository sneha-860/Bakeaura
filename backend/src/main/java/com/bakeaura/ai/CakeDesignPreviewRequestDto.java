package com.bakeaura.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class CakeDesignPreviewRequestDto {

    @NotBlank(message = "description is required")
    private String description;

    @NotBlank(message = "occasion is required")
    private String occasion;

    private String referenceImageBase64;
}
