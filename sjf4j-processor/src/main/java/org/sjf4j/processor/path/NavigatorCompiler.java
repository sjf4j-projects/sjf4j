package org.sjf4j.processor.path;

import org.sjf4j.processor.CompilationContext;
import org.sjf4j.processor.code.GeneratedClass;

/**
 * Resolves access semantics for analyzed Navigator plans before any generated
 * Java methods are written. Get/Put and Find produce independent typed plans.
 */
final class NavigatorCompiler {
    private final PathCompiler path;
    private final FindCompiler find;

    NavigatorCompiler(CompilationContext context) {
        this.path = new PathCompiler(context);
        this.find = new FindCompiler(context);
    }

    CompiledMethod compile(NavigatorPlan plan, GeneratedClass generated) {
        switch (plan.kind) {
            case GET:
                return CompiledMethod.path(path.compileGet(plan.get, generated));
            case PUT:
                return CompiledMethod.path(path.compilePut(plan.put, generated));
            case FIND:
                return CompiledMethod.find(find.compile(plan.method, generated,
                        plan.expression, plan.allowFallback));
            default:
                throw new AssertionError(plan.kind);
        }
    }

    static final class CompiledMethod {
        final PathPlan path;
        final FindPlan find;

        private CompiledMethod(PathPlan path, FindPlan find) {
            this.path = path;
            this.find = find;
        }

        static CompiledMethod path(PathPlan plan) {
            return plan == null ? null : new CompiledMethod(plan, null);
        }

        static CompiledMethod find(FindPlan plan) {
            return plan == null ? null : new CompiledMethod(null, plan);
        }
    }
}
