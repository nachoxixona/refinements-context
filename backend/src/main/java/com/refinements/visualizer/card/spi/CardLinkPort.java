package com.refinements.visualizer.card.spi;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What the card module needs to know about links between microservices and cards. Implemented by the microservice
 * module, so the compile-time dependency runs microservice → card only (research R5). Called synchronously inside
 * the caller's transaction.
 */
public interface CardLinkPort {

    /** The microservice linked to this card, if any. */
    Optional<UUID> linkedMicroservice(UUID cardId);

    /** card id → linked microservice id, for every linked card. */
    Map<UUID, UUID> linksByCard();

    /** Unlinks the card from its microservice (if any) and flushes, before the card row is deleted. */
    void unlinkCard(UUID cardId);
}
