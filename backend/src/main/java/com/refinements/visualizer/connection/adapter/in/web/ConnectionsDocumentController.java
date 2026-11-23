package com.refinements.visualizer.connection.adapter.in.web;

import com.refinements.visualizer.connection.application.port.in.ConnectionsDocumentUseCases;
import com.refinements.visualizer.connection.application.port.in.ConnectionsDocumentUseCases.Exported;
import com.refinements.visualizer.connection.application.port.in.ConnectionsDocumentUseCases.Outcome;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/connections-document")
public class ConnectionsDocumentController {

    private final ConnectionsDocumentUseCases documents;

    public ConnectionsDocumentController(ConnectionsDocumentUseCases documents) {
        this.documents = documents;
    }

    @GetMapping
    public ConnectionsDocumentBodies.Export export() {
        Exported e = documents.export();
        return new ConnectionsDocumentBodies.Export(
                ConnectionsDocumentDto.from(e.document()), e.text(), e.revision(), e.excludedDanglingConnections());
    }

    @PostMapping("/preview")
    public ConnectionsDocumentBodies.Preview preview(@Valid @RequestBody ConnectionsDocumentBodies.Submit submit) {
        Outcome o = documents.preview(submit.text());
        return new ConnectionsDocumentBodies.Preview(o.changes(), o.revision());
    }

    @PutMapping
    public ConnectionsDocumentBodies.Applied apply(@Valid @RequestBody ConnectionsDocumentBodies.Submit submit) {
        Outcome o = documents.apply(submit.text(), submit.baseRevision());
        return new ConnectionsDocumentBodies.Applied(o.changes(), o.revision());
    }
}
