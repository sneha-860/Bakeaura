package com.bakeaura.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUserStatusRequest {
    @NotNull
    private Boolean active;
}
