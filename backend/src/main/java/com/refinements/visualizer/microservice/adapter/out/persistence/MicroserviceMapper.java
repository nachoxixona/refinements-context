package com.refinements.visualizer.microservice.adapter.out.persistence;

import com.refinements.visualizer.microservice.domain.Microservice;

/** Microservice ↔ MicroserviceEntity. */
final class MicroserviceMapper {

    private MicroserviceMapper() {}

    static Microservice toDomain(MicroserviceEntity e) {
        return Microservice.restore(
                e.id,
                e.name,
                e.nameNormalized,
                e.description,
                e.team,
                e.repositoryUrl,
                e.docsUrl,
                e.specCardId,
                e.createdAt,
                e.updatedAt);
    }

    /** Copies the microservice's state onto a (new or managed) entity. */
    static void copy(Microservice m, MicroserviceEntity e) {
        e.name = m.getName();
        e.nameNormalized = m.getNameNormalized();
        e.description = m.getDescription();
        e.team = m.getTeam();
        e.repositoryUrl = m.getRepositoryUrl();
        e.docsUrl = m.getDocsUrl();
        e.specCardId = m.getSpecCardId();
        e.createdAt = m.getCreatedAt();
        e.updatedAt = m.getUpdatedAt();
    }
}
