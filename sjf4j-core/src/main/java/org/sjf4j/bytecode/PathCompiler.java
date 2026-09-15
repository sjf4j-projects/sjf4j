package org.sjf4j.bytecode;

import org.sjf4j.path.JsonPath;

import java.lang.reflect.Type;


/**
 * Service-provider interface for optional compiled JSON-path accessors.
 *
 * <p>This SPI is used by SJF4J modules to attach bytecode-backed implementations without making
 * the core artifact depend on a bytecode generator. Implementations are discovered through
 * {@link java.util.ServiceLoader} and receive a parsed path plus its declared root and value
 * types.
 *
 * <p>Implementations may reject paths whose shape or types cannot be compiled. Those constraints
 * may be stricter than {@link FallbackBytecodePath}'s reflective behavior.
 */
public interface PathCompiler {

    BytecodePath<?, ?> compilePath(JsonPath path, Type rootType, Type valueType);
}
