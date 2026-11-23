package com.refinements.visualizer.microservice.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record LinkCardRequest(@NotNull UUID cardId, Boolean replace) {

    public boolean replaceOrFalse() {
        return Boolean.TRUE.equals(replace);
    }
}
