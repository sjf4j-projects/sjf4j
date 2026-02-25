package org.sjf4j.facade;

/**
 * JSON facade interface with streaming support.
 */
public interface JsonFacade<R extends StreamingReader, W extends StreamingWriter> extends StreamingFacade<R, W> {
    // Marker interface extending StreamingFacade


}
