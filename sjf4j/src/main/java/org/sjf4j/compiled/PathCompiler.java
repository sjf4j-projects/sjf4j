package org.sjf4j.compiled;

import org.sjf4j.path.JsonPath;

import java.lang.reflect.Type;


/**
 * Optional compiled-path accelerator hook.
 *
 * <p>Implementations may return {@code null} to decline a path, or throw when they intentionally
 * enforce stricter shape/type requirements than the reflective fallback implementation.
 */
public interface PathCompiler {

    CompiledPath<?, ?> compilePath(JsonPath path, Type rootType, Type valueType);
}
