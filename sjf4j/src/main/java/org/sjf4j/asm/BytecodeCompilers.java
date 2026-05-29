package org.sjf4j.asm;


import org.sjf4j.exception.JsonException;
import org.sjf4j.node.Types;
import org.sjf4j.path.JsonPath;

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Objects;
import java.util.ServiceLoader;

final class BytecodeCompilers {

    static final PathCompiler PATH_COMPILER = loadPathCompiler();

    private static PathCompiler loadPathCompiler() {
        try {
            Iterator<PathCompiler> it = ServiceLoader.load(PathCompiler.class).iterator();
            return it.hasNext() ? it.next() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static BytecodePath<?, ?> compilePath(String pathExpr, Type rootType, Type valueType, boolean allowFallback) {
        Objects.requireNonNull(pathExpr, "pathExpr");
        Objects.requireNonNull(rootType, "rootType");
        Objects.requireNonNull(valueType, "valueType");
        Class<?> rootClazz = Types.rawClazz(rootType);
        Class<?> valueClazz = Types.rawClazz(valueType);

        JsonPath path = JsonPath.parse(pathExpr);
        if (path.length() < 2) {
            throw new JsonException("CompiledPath requires a non-root target path: '" + path + "'");
        }
        if (!path.isSinglePut()) {
            throw new JsonException("CompiledPath only supports a single target path with Name/Index/Append segments: '" + path + "'");
        }

        if (PATH_COMPILER != null) {
            return PATH_COMPILER.compilePath(path, rootType, valueType);
        }

        if (allowFallback) {
            return new FallbackBytecodePath<>(path, rootClazz, valueClazz);
        }

        String message = "CompiledPath requires an optional bytecode compiler for '" + path.toExpr() +
                "' (rootType=" + Types.name(rootType) + ", valueType=" + Types.name(valueType) +
                "). Add sjf4j-bytecode to the runtime classpath, or instantiate FallbackCompiledPath explicitly.";
        throw new JsonException(message);
    }

}
