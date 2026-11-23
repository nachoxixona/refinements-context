package com.refinements.visualizer.microservice.adapter.out.card;

import com.refinements.visualizer.card.SpecCardApi;
import com.refinements.visualizer.microservice.application.port.out.CardCatalog;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** {@link CardCatalog} through the card module's API. */
@Component
class CardCatalogAdapter implements CardCatalog {

    private final SpecCardApi cards;

    CardCatalogAdapter(SpecCardApi cards) {
        this.cards = cards;
    }

    @Override
    public boolean exists(UUID cardId) {
        return cards.exists(cardId);
    }
}
