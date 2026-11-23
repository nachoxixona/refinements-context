package com.refinements.visualizer.card.adapter.in.web;

/** Optional body of {@code POST /api/cards/{cardId}/sync}. Required for pasted cards. */
public record SyncCardRequest(String content) {}
