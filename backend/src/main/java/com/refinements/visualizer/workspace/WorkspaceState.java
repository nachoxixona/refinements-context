package com.refinements.visualizer.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Single row holding data-model.md §6 WorkspaceState.revision (research R7). */
@Entity
@Table(name = "workspace_state")
public class WorkspaceState {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(name = "revision", nullable = false)
    private long revision;

    protected WorkspaceState() {}

    public long getRevision() {
        return revision;
    }

    long increment() {
        return ++revision;
    }
}
