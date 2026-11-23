package com.refinements.visualizer.microservice.adapter.out.card;

import com.refinements.visualizer.card.spi.CardLinkPort;
import com.refinements.visualizer.microservice.Microservice;
import com.refinements.visualizer.microservice.MicroserviceRepository;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The microservice module's side of {@link CardLinkPort} (research R5). */
@Component
class CardLinkAdapter implements CardLinkPort {

    private final MicroserviceRepository microservices;
    private final Clock clock;

    CardLinkAdapter(MicroserviceRepository microservices, Clock clock) {
        this.microservices = microservices;
        this.clock = clock;
    }

    @Override
    public Optional<UUID> linkedMicroservice(UUID cardId) {
        return microservices.findBySpecCardId(cardId).map(Microservice::getId);
    }

    @Override
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
    public void unlinkCard(UUID cardId) {
        microservices.findBySpecCardId(cardId).ifPresent(m -> m.link(null, clock.instant()));
        microservices.flush();
    }
}
