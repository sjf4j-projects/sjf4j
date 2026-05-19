package org.sjf4j.compiled;


import org.sjf4j.path.JsonPath;

import java.lang.reflect.Type;
import java.util.Iterator;
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

    public static CompiledPath<?, ?> compilePath(JsonPath path, Type rootType, Type valueType) {
        if (PATH_COMPILER != null) {
            return PATH_COMPILER.compilePath(path, rootType, valueType);
        }
        return null;
    }

}
