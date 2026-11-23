package com.refinements.visualizer.card.dto;

/** Optional body of {@code POST /api/cards/{cardId}/sync}. Required for pasted cards. */
public record SyncCardRequest(String content) {}
