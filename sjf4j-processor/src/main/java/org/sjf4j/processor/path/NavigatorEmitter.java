package org.sjf4j.processor.path;

import org.sjf4j.processor.CompilationContext;
import org.sjf4j.processor.code.GeneratedClass;

/** Contributes already compiled navigator methods to the generated class. */
final class NavigatorEmitter {
    private final PathEmitter path;
    private final FindEmitter find;

    NavigatorEmitter(CompilationContext context) {
        this.path = new PathEmitter(context);
        this.find = new FindEmitter(context);
    }

    void emit(NavigatorCompiler.CompiledMethod method, GeneratedClass generated) {
        if (method.path != null) {
            path.emit(method.path, generated);
        } else if (method.find != null) {
            find.emit(method.find, generated);
        } else {
            throw new AssertionError("empty compiled navigator method");
        }
    }
}
