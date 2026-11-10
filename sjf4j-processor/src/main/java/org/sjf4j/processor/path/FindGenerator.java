package org.sjf4j.processor.path;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;

import javax.lang.model.element.ExecutableElement;

/**
 * Coordinates the compile-time find pipeline: resolve a typed traversal plan,
 * then emit direct Java loops. Only explicitly permitted fallback paths use the
 * runtime JSONPath evaluator.
 */
public final class FindGenerator {
    private final ProcessorContext context;
    private final FindEmitter emitter;

    public FindGenerator(ProcessorContext context) {
        this.context = context;
        this.emitter = new FindEmitter(context);
    }

    /** Validates and generates one @FindByPath method. */
    public void generate(ExecutableElement method, GeneratedClass generated,
                         String expression, boolean allowFallback) {
        FindPlan plan = new FindCompiler(context).compile(method, generated,
                expression, allowFallback);
        if (plan != null && generated.isValid()) {
            emitter.emit(plan, generated);
        }
    }
}
