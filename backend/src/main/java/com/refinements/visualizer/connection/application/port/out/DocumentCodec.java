package com.refinements.visualizer.connection.application.port.out;

import com.refinements.visualizer.connection.domain.document.ConnectionsDocument;
import com.refinements.visualizer.connection.domain.document.ConnectionsDocumentModel;

/** Turns connections document text into a validated model and back (research R4–R6). */
public interface DocumentCodec {

    /**
     * @throws com.refinements.visualizer.shared.errors.ConnectionsDocumentInvalidException with every error, located,
     *     when the text is not a valid document
     */
    ConnectionsDocumentModel read(String text);

    String write(ConnectionsDocument document);
}
