package com.refinements.visualizer.layout.application.port.in;

import com.refinements.visualizer.shared.XYPosition;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What the layout module offers its inbound adapters. */
public interface LayoutUseCases {

    /** A full replacement of positions and collapsed cards, based on layout {@code version}. */
    record SaveLayout(int version, Map<String, XYPosition> positions, List<UUID> collapsedCards) {}

    /** Saves with optimistic locking and returns the new version. */
    int save(SaveLayout command);
}
