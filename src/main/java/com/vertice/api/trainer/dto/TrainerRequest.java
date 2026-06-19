package com.vertice.api.trainer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record TrainerRequest(
        @NotBlank String name,
        @NotBlank @Email String email
) {}
