package com.refinements.visualizer.card;

import com.refinements.visualizer.card.dto.ImportCardRequest;
import com.refinements.visualizer.card.dto.SpecCardSummary;
import com.refinements.visualizer.card.dto.SyncCardRequest;
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

    private final SpecCardService service;

    public SpecCardController(SpecCardService service) {
        this.service = service;
    }

    @GetMapping
    public List<SpecCardSummary> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<SpecCardSummary> importCard(@Valid @RequestBody ImportCardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.importCard(request));
    }

    @DeleteMapping("/{cardId}")
    public ResponseEntity<Void> delete(@PathVariable UUID cardId) {
        service.delete(cardId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{cardId}/sync")
    public SpecCardSummary sync(@PathVariable UUID cardId, @RequestBody(required = false) SyncCardRequest request) {
        return service.sync(cardId, request == null ? null : request.content());
    }
}
