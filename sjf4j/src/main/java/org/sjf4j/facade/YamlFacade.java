package org.sjf4j.facade;


/**
 * YAML facade interface with streaming support.
 */
public interface YamlFacade<R extends StreamingReader, W extends StreamingWriter> extends StreamingFacade<R, W> {}
