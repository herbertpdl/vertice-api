package com.vertice.api.student.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record StudentRequest(
        @NotBlank String name,
        @NotBlank @Email String email
) {}
