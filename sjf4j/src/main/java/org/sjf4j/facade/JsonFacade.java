package org.sjf4j.facade;

/**
 * A unified facade interface for JSON processing that encapsulates the implementation details of various JSON parsing libraries.
 * <p>
 * JsonFacade extends {@link StreamingFacade} to provide streaming read/write capabilities for JSON data.
 * Different JSON library implementations (such as Jackson, Gson, and Fastjson2) all provide unified access
 * through this interface, allowing users to switch between different JSON parsers without modifying their code.
 * <p>
 * SJF4J automatically detects available JSON libraries on the classpath and creates the appropriate Facade instances.
 * The default priority order is: Jackson > Gson > Fastjson2 > Built-in Simple parser.
 *
 * @param <R> the type of FacadeReader associated with this facade
 * @param <W> the type of FacadeWriter associated with this facade
 */
public interface JsonFacade<R extends StreamingReader, W extends StreamingWriter> extends StreamingFacade<R, W> {
    // Marker interface extending StreamingFacade


}
