package com.refinements.visualizer.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.refinements.visualizer.canvas.CanvasService;
import com.refinements.visualizer.canvas.dto.CanvasResponse;
import com.refinements.visualizer.card.SourceType;
import com.refinements.visualizer.card.application.port.in.SpecCardUseCases;
import com.refinements.visualizer.microservice.application.port.in.MicroserviceUseCases;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** SC-001 and the plan's scale: 100 microservices, 100 cards, 500 connections. */
class PerformanceSmokeIT extends PostgresIntegrationTest {

    private static final int SERVICES = 100;

    @Autowired
    SpecCardUseCases cards;

    @Autowired
    MicroserviceUseCases microservices;

    @Autowired
    CanvasService canvasService;

    static String syntheticSpec(int i) {
        StringBuilder sb = new StringBuilder("openapi: 3.0.3\ninfo:\n  title: Synthetic ")
                .append(i)
                .append("\n  version: '1'\npaths:\n");
        for (int j = 0; j < 10; j++) {
            sb.append("  /r")
                    .append(j)
                    .append("/{s")
                    .append(i)
                    .append("Id}:\n    get:\n      parameters:\n        - { name: s")
                    .append(i)
                    .append("Id, in: path, required: true, schema: { type: string } }\n")
                    .append("      responses:\n        '200':\n          description: ok\n          content:\n")
                    .append("            application/json:\n              schema:\n                type: object\n")
                    .append("                properties:\n                  label: { type: string }\n");
        }
        return sb.toString();
    }

    @Test
    void largeSpecImportAndAFullCanvas() throws Exception {
        long start = System.nanoTime();
        MvcResult large = importPaste("oas30/large-200-endpoints.yaml");
        long importMillis = (System.nanoTime() - start) / 1_000_000;
        assertThat(large.getResponse().getStatus()).isEqualTo(201);
        assertThat(read(large).get("endpointCount").asInt()).isEqualTo(200);
        assertThat(importMillis).as("200-endpoint import (SC-001)").isLessThan(30_000);

        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < SERVICES; i++) {
            UUID card = cards.importCard(new SpecCardUseCases.ImportCard(SourceType.PASTE, syntheticSpec(i), null))
                    .card()
                    .id();
            UUID ms = microservices
                    .create(new MicroserviceUseCases.NewMicroservice("Service " + i, null, null, null, null))
                    .microservice()
                    .id();
            microservices.link(ms, card, false);
            ids.add(ms);
        }
        for (int i = 0; i < 500; i++) {
            UUID source = ids.get(i % SERVICES);
            UUID target = ids.get((i * 7 + 3) % SERVICES);
            if (source.equals(target)) {
                target = ids.get((i + 1) % SERVICES);
            }
            jdbcConnection(source, target, "Connection " + i);
        }

        CanvasResponse c = null;
        for (int warm = 0; warm < 5; warm++) {
            c = canvasService.canvas();
        }
        start = System.nanoTime();
        c = canvasService.canvas();
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertThat(c.nodes()).hasSize(SERVICES * 2 + 1);
        assertThat(c.edges()).hasSize(500);
        assertThat(millis).as("warm canvas build").isLessThan(300);
    }

    private void jdbcConnection(UUID source, UUID target, String name) throws Exception {
        String s = jdbc.queryForObject("SELECT name FROM microservice WHERE id = ?", String.class, source);
        String t = jdbc.queryForObject("SELECT name FROM microservice WHERE id = ?", String.class, target);
        jdbc.update(
                "INSERT INTO connection (id, name, name_normalized, source_id, target_id, source_name, target_name,"
                        + " source_name_normalized, target_name_normalized, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())",
                UUID.randomUUID(),
                name,
                name.toLowerCase(),
                source,
                target,
                s,
                t,
                s.toLowerCase(),
                t.toLowerCase());
    }
}
