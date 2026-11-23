package com.refinements.visualizer.card.adapter.in.web;

import com.refinements.visualizer.card.application.port.in.SpecCardUseCases;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
public class SpecCardController {

    private final SpecCardUseCases cards;

    SpecCardController(SpecCardUseCases cards) {
        this.cards = cards;
    }

    @GetMapping
    public List<SpecCardSummary> list() {
        return cards.list().stream().map(SpecCardSummary::from).toList();
    }

    @PostMapping
    public ResponseEntity<SpecCardSummary> importCard(@Valid @RequestBody ImportCardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SpecCardSummary.from(cards.importCard(
                        new SpecCardUseCases.ImportCard(request.sourceType(), request.content(), request.url()))));
    }

    @DeleteMapping("/{cardId}")
    public ResponseEntity<Void> delete(@PathVariable UUID cardId) {
        cards.delete(cardId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{cardId}/sync")
    public SpecCardSummary sync(@PathVariable UUID cardId, @RequestBody(required = false) SyncCardRequest request) {
        return SpecCardSummary.from(cards.sync(cardId, request == null ? null : request.content()));
    }
}
