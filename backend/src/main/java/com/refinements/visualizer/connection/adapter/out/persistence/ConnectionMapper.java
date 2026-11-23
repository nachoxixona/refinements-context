package com.refinements.visualizer.connection.adapter.out.persistence;

import com.refinements.visualizer.connection.domain.Connection;
import com.refinements.visualizer.connection.domain.Flow;

/** Connection ↔ ConnectionEntity and Flow ↔ FlowEntity. */
final class ConnectionMapper {

    private ConnectionMapper() {}

    static Connection toDomain(ConnectionEntity e) {
        return Connection.restore(
                e.id,
                e.name,
                e.nameNormalized,
                e.kind,
                e.description,
                e.flowId,
                e.step,
                e.endpoint,
                e.sourceId,
                e.targetId,
                e.sourceName,
                e.targetName,
                e.sourceNameNormalized,
                e.targetNameNormalized,
                e.createdAt,
                e.updatedAt);
    }

    /** Copies the connection's state onto a (new or managed) entity. */
    static void copy(Connection c, ConnectionEntity e) {
        e.name = c.getName();
        e.nameNormalized = c.getNameNormalized();
        e.kind = c.getKind();
        e.description = c.getDescription();
        e.flowId = c.getFlowId();
        e.step = c.getStep();
        e.endpoint = c.getEndpoint();
        e.sourceId = c.getSourceId();
        e.targetId = c.getTargetId();
        e.sourceName = c.getSourceName();
        e.targetName = c.getTargetName();
        e.sourceNameNormalized = c.getSourceNameNormalized();
        e.targetNameNormalized = c.getTargetNameNormalized();
        e.createdAt = c.getCreatedAt();
        e.updatedAt = c.getUpdatedAt();
    }

    static Flow toDomain(FlowEntity e) {
        return Flow.restore(e.id, e.name, e.nameNormalized, e.color, e.createdAt, e.updatedAt);
    }

    static void copy(Flow f, FlowEntity e) {
        e.name = f.getName();
        e.nameNormalized = f.getNameNormalized();
        e.color = f.getColor();
        e.createdAt = f.getCreatedAt();
        e.updatedAt = f.getUpdatedAt();
    }
}
