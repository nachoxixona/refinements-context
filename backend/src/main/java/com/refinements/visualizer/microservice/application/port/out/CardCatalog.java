package com.refinements.visualizer.microservice.application.port.out;

import java.util.UUID;

/** Which spec cards exist (the card module behind it). */
public interface CardCatalog {

    boolean exists(UUID cardId);
}
