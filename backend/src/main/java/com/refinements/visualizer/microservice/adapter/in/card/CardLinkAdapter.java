package com.refinements.visualizer.microservice.adapter.in.card;

import com.refinements.visualizer.card.spi.CardLinkPort;
import com.refinements.visualizer.microservice.application.port.in.CardLinks;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The card module calls in here through its {@link CardLinkPort} (research R5). */
@Component
class CardLinkAdapter implements CardLinkPort {

    private final CardLinks links;

    CardLinkAdapter(CardLinks links) {
        this.links = links;
    }

    @Override
    public Optional<UUID> linkedMicroservice(UUID cardId) {
        return links.linkedMicroservice(cardId);
    }

    @Override
    public Map<UUID, UUID> linksByCard() {
        return links.linksByCard();
    }

    @Override
    public void unlinkCard(UUID cardId) {
        links.unlinkCard(cardId);
    }
}
