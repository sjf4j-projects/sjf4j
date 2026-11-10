package org.sjf4j.processor.path;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;

/**
 * Resolves access semantics for analyzed Navigator plans before any generated
 * Java methods are written. Get/Put reuse the existing PathEmitter access
 * compiler; Find uses composable, statically typed traversal steps.
 */
final class NavigatorCompiler {
    private final PathEmitter single;
    private final FindCompiler find;

    NavigatorCompiler(ProcessorContext context) {
        this.single = new PathEmitter(context);
        this.find = new FindCompiler(context);
    }

    CompiledMethod compile(NavigatorPlan plan, GeneratedClass generated) {
        switch (plan.kind) {
            case GET:
                return CompiledMethod.single(single.compileGet(plan.get, generated));
            case PUT:
                return CompiledMethod.single(single.compilePut(plan.put, generated));
            case FIND:
                return CompiledMethod.find(find.compile(plan.method, generated,
                        plan.expression, plan.allowFallback));
            default:
                throw new AssertionError(plan.kind);
        }
    }

    static final class CompiledMethod {
        final GeneratedClass.Member single;
        final FindPlan find;

        private CompiledMethod(GeneratedClass.Member single, FindPlan find) {
            this.single = single;
            this.find = find;
        }

        static CompiledMethod single(GeneratedClass.Member member) {
            return member == null ? null : new CompiledMethod(member, null);
        }

        static CompiledMethod find(FindPlan plan) {
            return plan == null ? null : new CompiledMethod(null, plan);
        }
    }
}
