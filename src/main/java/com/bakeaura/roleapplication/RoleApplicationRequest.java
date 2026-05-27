package com.bakeaura.roleapplication;

import com.bakeaura.enums.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleApplicationRequest {
    @NotNull
    private Role requestedRole;

    @Size(max = 1000)
    private String message;
}
