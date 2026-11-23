package com.refinements.visualizer.workspace.application;

import com.refinements.visualizer.workspace.WorkspaceRevision;
import com.refinements.visualizer.workspace.application.port.out.WorkspaceStateStore;
import com.refinements.visualizer.workspace.domain.WorkspaceState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class WorkspaceRevisionService implements WorkspaceRevision {

    private final WorkspaceStateStore store;

    WorkspaceRevisionService(WorkspaceStateStore store) {
        this.store = store;
    }

    @Override
    @Transactional(readOnly = true)
    public long current() {
        return store.currentRevision();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long bump() {
        WorkspaceState state = store.lock();
        long next = state.increment();
        store.save(state);
        return next;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void check(Long baseRevision) {
        store.lock().checkFresh(baseRevision);
    }
}
