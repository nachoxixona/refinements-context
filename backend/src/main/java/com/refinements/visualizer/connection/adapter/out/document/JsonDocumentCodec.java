package com.refinements.visualizer.connection.adapter.out.document;

import com.refinements.visualizer.connection.application.port.out.DocumentCodec;
import com.refinements.visualizer.connection.domain.document.ConnectionsDocument;
import com.refinements.visualizer.connection.domain.document.ConnectionsDocumentModel;
import org.springframework.stereotype.Component;

/** {@link DocumentCodec} on Jackson: {@link ConnectionsDocumentReader} and {@link ConnectionsDocumentWriter}. */
@Component
class JsonDocumentCodec implements DocumentCodec {

    private final ConnectionsDocumentReader reader;
    private final ConnectionsDocumentWriter writer;

    JsonDocumentCodec(ConnectionsDocumentReader reader, ConnectionsDocumentWriter writer) {
        this.reader = reader;
        this.writer = writer;
    }

    @Override
    public ConnectionsDocumentModel read(String text) {
        return reader.read(text);
    }

    @Override
    public String write(ConnectionsDocument document) {
        return writer.write(document);
    }
}
