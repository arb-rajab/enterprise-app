package com.enterpriseapp.procureflow.user.dto;

import com.enterpriseapp.procureflow.user.RoleName;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record UpdateRolesRequest(@NotEmpty Set<RoleName> roles) {}
