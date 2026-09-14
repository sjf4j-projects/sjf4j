package org.sjf4j.binding;


import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.facade.StreamingReader;
import org.sjf4j.facade.StreamingWriter;

/**
 * YAML facade interface with streaming support.
 */
public interface YamlBinding<R extends StreamingReader, W extends StreamingWriter> extends StreamingFacade<R, W> {}
