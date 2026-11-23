package com.refinements.visualizer.microservice.application.port.in;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Card-link questions the card module asks (behind {@code card.spi.CardLinkPort}). */
public interface CardLinks {

    Optional<UUID> linkedMicroservice(UUID cardId);

    Map<UUID, UUID> linksByCard();

    /** Unlinks the card from its microservice (if any) and flushes. */
    void unlinkCard(UUID cardId);
}
