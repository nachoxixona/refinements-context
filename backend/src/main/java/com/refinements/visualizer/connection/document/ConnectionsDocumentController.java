package com.refinements.visualizer.connection.document;

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

    private final ConnectionsDocumentService service;

    public ConnectionsDocumentController(ConnectionsDocumentService service) {
        this.service = service;
    }

    @GetMapping
    public ConnectionsDocumentApi.Export export() {
        return service.export();
    }

    @PostMapping("/preview")
    public ConnectionsDocumentApi.Preview preview(@Valid @RequestBody ConnectionsDocumentApi.Submit submit) {
        return service.preview(submit.text());
    }

    @PutMapping
    public ConnectionsDocumentApi.Applied apply(@Valid @RequestBody ConnectionsDocumentApi.Submit submit) {
        return service.apply(submit.text(), submit.baseRevision());
    }
}
