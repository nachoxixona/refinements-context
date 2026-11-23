package com.refinements.visualizer.layout.adapter.out.modules;

import com.refinements.visualizer.card.SpecCardApi;
import com.refinements.visualizer.layout.application.port.out.LayoutTargets;
import com.refinements.visualizer.microservice.MicroserviceApi;
import com.refinements.visualizer.microservice.MicroserviceView;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** {@link LayoutTargets} through the microservice and card modules' APIs. */
@Component
class LayoutTargetsAdapter implements LayoutTargets {

    private final MicroserviceApi microservices;
    private final SpecCardApi cards;

    LayoutTargetsAdapter(MicroserviceApi microservices, SpecCardApi cards) {
        this.microservices = microservices;
        this.cards = cards;
    }

    @Override
    public Map<UUID, UUID> microserviceCards() {
        Map<UUID, UUID> result = new HashMap<>();
        for (MicroserviceView m : microservices.findAll()) {
            result.put(m.id(), m.specCardId());
        }
        return result;
    }

    @Override
    public Set<UUID> cardIds() {
        return Set.copyOf(cards.allIds());
    }
}
