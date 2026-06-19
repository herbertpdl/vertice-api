package com.vertice.api.student.dto;

public record StudentResponse(
        Long id,
        String name,
        String email
) {}
