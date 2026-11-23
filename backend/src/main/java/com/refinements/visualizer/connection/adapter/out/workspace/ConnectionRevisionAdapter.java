package com.refinements.visualizer.connection.adapter.out.workspace;

import com.refinements.visualizer.connection.application.port.out.Revision;
import com.refinements.visualizer.workspace.WorkspaceRevision;
import org.springframework.stereotype.Component;

/** {@link Revision} through the workspace module's API. */
@Component
class ConnectionRevisionAdapter implements Revision {

    private final WorkspaceRevision revision;

    ConnectionRevisionAdapter(WorkspaceRevision revision) {
        this.revision = revision;
    }

    @Override
    public long current() {
        return revision.current();
    }

    @Override
    public long bump() {
        return revision.bump();
    }

    @Override
    public void check(Long baseRevision) {
        revision.check(baseRevision);
    }
}
