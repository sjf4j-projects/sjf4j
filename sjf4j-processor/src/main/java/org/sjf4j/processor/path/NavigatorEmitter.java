package org.sjf4j.processor.path;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;

/** Contributes already compiled navigator methods to the generated class. */
final class NavigatorEmitter {
    private final FindEmitter find;

    NavigatorEmitter(ProcessorContext context) {
        this.find = new FindEmitter(context);
    }

    void emit(NavigatorCompiler.CompiledMethod method, GeneratedClass generated) {
        if (method.single != null) {
            generated.addMethod(method.single);
        } else if (method.find != null) {
            find.emit(method.find, generated);
        } else {
            throw new AssertionError("empty compiled navigator method");
        }
    }
}
