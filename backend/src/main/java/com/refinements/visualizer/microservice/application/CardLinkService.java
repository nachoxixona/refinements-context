package com.refinements.visualizer.microservice.application;

import com.refinements.visualizer.microservice.application.port.in.CardLinks;
import com.refinements.visualizer.microservice.application.port.out.MicroserviceStore;
import com.refinements.visualizer.microservice.domain.Microservice;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers the card module's link questions. Kept apart from {@link MicroserviceService} on purpose: that service
 * calls the card module, and the card module calls back here, so sharing one bean would make a runtime cycle.
 * This service depends only on the store and the clock.
 */
@Service
class CardLinkService implements CardLinks {

    private final MicroserviceStore microservices;
    private final Clock clock;

    CardLinkService(MicroserviceStore microservices, Clock clock) {
        this.microservices = microservices;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> linkedMicroservice(UUID cardId) {
        return microservices.findBySpecCardId(cardId).map(Microservice::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UUID> linksByCard() {
        Map<UUID, UUID> links = new HashMap<>();
        for (Microservice m : microservices.findAll()) {
            if (m.getSpecCardId() != null) {
                links.put(m.getSpecCardId(), m.getId());
            }
        }
        return links;
    }

    @Override
    @Transactional
    public void unlinkCard(UUID cardId) {
        Optional<Microservice> m = microservices.findBySpecCardId(cardId);
        if (m.isPresent()) {
            m.get().link(null, clock.instant());
            microservices.save(m.get()); // flushes before the card row is deleted
        }
    }
}
