package org.sjf4j.bytecode;

import org.sjf4j.path.JsonPath;

import java.lang.reflect.Type;


/**
 * Optional compiled-path accelerator hook.
 *
 * <p>This SPI is used by SJF4J modules to attach bytecode-backed implementations without making
 * the core artifact depend on ASM. Implementations should throw when they intentionally enforce
 * stricter shape/type requirements than the reflective fallback implementation.
 */
public interface PathCompiler {

    BytecodePath<?, ?> compilePath(JsonPath path, Type rootType, Type valueType);
}
