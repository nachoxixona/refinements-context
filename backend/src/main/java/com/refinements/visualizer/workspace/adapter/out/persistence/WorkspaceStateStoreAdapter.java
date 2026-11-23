package com.refinements.visualizer.workspace.adapter.out.persistence;

import com.refinements.visualizer.workspace.application.port.out.WorkspaceStateStore;
import com.refinements.visualizer.workspace.domain.WorkspaceState;
import org.springframework.stereotype.Component;

/** {@link WorkspaceStateStore} on Spring Data JPA, with the row lock of research R7 (003). */
@Component
class WorkspaceStateStoreAdapter implements WorkspaceStateStore {

    private final WorkspaceStateJpaRepository repository;

    WorkspaceStateStoreAdapter(WorkspaceStateJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public long currentRevision() {
        return repository
                .findById(WorkspaceStateEntity.SINGLETON_ID)
                .map(e -> e.revision)
                .orElse(0L);
    }

    @Override
    public WorkspaceState lock() {
        return new WorkspaceState(locked().revision);
    }

    @Override
    public void save(WorkspaceState state) {
        // lock() loaded and locked the row in this transaction, so findById returns that managed entity from the
        // persistence context (no second query); dirty checking writes it at commit, as before
        WorkspaceStateEntity e = repository
                .findById(WorkspaceStateEntity.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("workspace_state row 1 is missing"));
        e.revision = state.getRevision();
    }

    private WorkspaceStateEntity locked() {
        return repository
                .lockSingleton()
                .orElseThrow(() -> new IllegalStateException("workspace_state row 1 is missing"));
    }
}
