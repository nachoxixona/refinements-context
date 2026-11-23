package com.refinements.visualizer.microservice.adapter.out.workspace;

import com.refinements.visualizer.microservice.application.port.out.Revision;
import com.refinements.visualizer.workspace.WorkspaceRevision;
import org.springframework.stereotype.Component;

/** {@link Revision} through the workspace module's API. */
@Component
class RevisionAdapter implements Revision {

    private final WorkspaceRevision revision;

    RevisionAdapter(WorkspaceRevision revision) {
        this.revision = revision;
    }

    @Override
    public long bump() {
        return revision.bump();
    }
}
