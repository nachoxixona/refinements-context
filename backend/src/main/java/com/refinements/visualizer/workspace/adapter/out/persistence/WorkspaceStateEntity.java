package com.refinements.visualizer.workspace.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The single row of {@code workspace_state} (V1__schema.sql). */
@Entity
@Table(name = "workspace_state")
class WorkspaceStateEntity {

    static final short SINGLETON_ID = 1;

    @Id
    Short id;

    @Column(name = "revision", nullable = false)
    long revision;

    protected WorkspaceStateEntity() {}
}
