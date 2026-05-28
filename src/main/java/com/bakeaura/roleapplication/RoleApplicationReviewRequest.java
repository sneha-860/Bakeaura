package com.bakeaura.roleapplication;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleApplicationReviewRequest {
    @Size(max = 1000)
    private String reviewNote;
}
