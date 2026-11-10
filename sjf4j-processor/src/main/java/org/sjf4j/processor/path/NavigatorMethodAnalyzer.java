package org.sjf4j.processor.path;

import org.sjf4j.annotation.path.EnsurePutByPath;
import org.sjf4j.annotation.path.EnsurePutIfAbsentByPath;
import org.sjf4j.annotation.path.FindByPath;
import org.sjf4j.annotation.path.GetByPath;
import org.sjf4j.annotation.path.PutByPath;
import org.sjf4j.annotation.path.PutIfParentPresentByPath;
import org.sjf4j.exception.NodeException;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.method.ResolvedMethod;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Performs operation selection, signature validation, and path parameter
 * binding. Does not resolve node access or generate Java source.
 */
final class NavigatorMethodAnalyzer {
    private final ProcessorContext context;

    NavigatorMethodAnalyzer(ProcessorContext context) {
        this.context = context;
    }

    NavigatorPlan analyze(ResolvedMethod resolved, GeneratedClass generated,
                          boolean fallback) {
        ExecutableElement method = resolved.declaration();
        if (!method.getTypeParameters().isEmpty()) {
            error(method, generated, "@CompiledNavigator method cannot declare type parameters");
            return null;
        }

        Operation operation = resolveOperation(method, generated);
        if (operation == null || !generated.isValid()) return null;

        switch (operation.kind) {
            case GET:
                return analyzeGet(method, generated, operation);
            case PUT:
                return analyzePut(method, generated, operation);
            case FIND:
                return NavigatorPlan.find(method, operation.expression, fallback);
            default:
                throw new AssertionError(operation.kind);
        }
    }

    private NavigatorPlan analyzeGet(ExecutableElement method, GeneratedClass generated,
                                     Operation operation) {
        List<? extends VariableElement> parameters = method.getParameters();
        if (parameters.isEmpty()) {
            error(method, generated, "@GetByPath method must have a root parameter");
            return null;
        }
        if (method.getReturnType().getKind() == TypeKind.VOID) {
            error(method, generated, "@GetByPath method must return the path value");
            return null;
        }
        JsonPath path = parsePath(method, generated, operation.expression);
        if (path == null) return null;
        if (!path.isSingleGet()) {
            error(method, generated, "@GetByPath supports only Name/Index/Param paths");
            return null;
        }
        Map<String, VariableElement> params = resolvePathParameters(
                method, generated, path, 1, parameters.size(), operation.annotation);
        if (params == null) return null;
        return NavigatorPlan.get(new NavigatorPlan.Get(method, parameters.get(0), path, params));
    }

    private NavigatorPlan analyzePut(ExecutableElement method, GeneratedClass generated,
                                     Operation operation) {
        List<? extends VariableElement> parameters = method.getParameters();
        if (parameters.size() < 2) {
            error(method, generated, operation.annotation + " method must have root and value parameters");
            return null;
        }

        JsonPath path = parsePath(method, generated, operation.expression);
        if (path == null) return null;
        if (!path.isSinglePut()) {
            error(method, generated, operation.annotation +
                    " supports only Name/Index/Param/Append paths");
            return null;
        }
        if (!operation.ensure && (path.appendCount() > 1 ||
                (path.appendCount() != 0 && !(path.tail() instanceof PathSegment.Append)))) {
            error(method, generated, operation.annotation +
                    " append segment must be the final path segment");
            return null;
        }

        Map<String, VariableElement> params = resolvePathParameters(
                method, generated, path, 1, parameters.size() - 1, operation.annotation);
        if (params == null) return null;
        return NavigatorPlan.put(new NavigatorPlan.Put(
                method, parameters.get(0), parameters.get(parameters.size() - 1),
                path, params, operation.annotation, operation.ensure,
                operation.ifParentPresent, operation.ifAbsent));
    }

    private Operation resolveOperation(ExecutableElement method, GeneratedClass generated) {
        Operation operation = null;
        GetByPath get = method.getAnnotation(GetByPath.class);
        if (get != null) operation = select(method, generated, operation,
                new Operation(NavigatorPlan.Kind.GET, "@GetByPath", get.value(), false, false, false));
        PutByPath put = method.getAnnotation(PutByPath.class);
        if (put != null) operation = select(method, generated, operation,
                new Operation(NavigatorPlan.Kind.PUT, "@PutByPath", put.value(), false, false, false));
        PutIfParentPresentByPath parent = method.getAnnotation(PutIfParentPresentByPath.class);
        if (parent != null) operation = select(method, generated, operation,
                new Operation(NavigatorPlan.Kind.PUT, "@PutIfParentPresentByPath",
                        parent.value(), false, true, false));
        EnsurePutByPath ensure = method.getAnnotation(EnsurePutByPath.class);
        if (ensure != null) operation = select(method, generated, operation,
                new Operation(NavigatorPlan.Kind.PUT, "@EnsurePutByPath",
                        ensure.value(), true, false, false));
        EnsurePutIfAbsentByPath absent = method.getAnnotation(EnsurePutIfAbsentByPath.class);
        if (absent != null) operation = select(method, generated, operation,
                new Operation(NavigatorPlan.Kind.PUT, "@EnsurePutIfAbsentByPath",
                        absent.value(), true, false, true));
        FindByPath find = method.getAnnotation(FindByPath.class);
        if (find != null) operation = select(method, generated, operation,
                new Operation(NavigatorPlan.Kind.FIND, "@FindByPath", find.value(),
                        false, false, false));

        if (operation == null) error(method, generated,
                "@CompiledNavigator abstract method must declare a path operation annotation");
        return operation;
    }

    private Operation select(ExecutableElement method, GeneratedClass generated,
                             Operation previous, Operation next) {
        if (previous == null) return next;
        error(method, generated, "Path operation annotations cannot be used together: " +
                previous.annotation + " and " + next.annotation);
        return previous;
    }

    private JsonPath parsePath(ExecutableElement method, GeneratedClass generated,
                               String expression) {
        JsonPath path;
        try {
            path = JsonPath.parse(expression);
        } catch (NodeException e) {
            error(method, generated, "Invalid JSON Path value: " + e.getMessage() +
                    " (expression '" + expression + "')");
            return null;
        }
        if (path.length() < 2) {
            error(method, generated,
                    "Invalid JSON Path value: requires a non-root path, but was '" + expression + "'");
            return null;
        }
        return path;
    }

    private Map<String, VariableElement> resolvePathParameters(
            ExecutableElement method, GeneratedClass generated, JsonPath path,
            int from, int to, String annotation) {
        List<? extends VariableElement> parameters = method.getParameters();
        Map<String, VariableElement> available = new LinkedHashMap<>();
        for (int i = from; i < to; i++) {
            VariableElement parameter = parameters.get(i);
            available.put(parameter.getSimpleName().toString(), parameter);
        }

        Map<String, VariableElement> used = new LinkedHashMap<>();
        for (int i = 1; i < path.segments().length; i++) {
            PathSegment segment = path.segments()[i];
            if (!(segment instanceof PathSegment.Param)) continue;
            String name = ((PathSegment.Param) segment).param;
            VariableElement parameter = available.get(name);
            if (parameter == null) {
                error(method, generated, annotation +
                        " path parameter '{" + name + "}' has no matching method parameter");
                return null;
            }
            used.put(name, parameter);
        }
        for (Map.Entry<String, VariableElement> entry : available.entrySet()) {
            if (!used.containsKey(entry.getKey())) {
                error(method, generated, annotation +
                        " method parameter '" + entry.getKey() + "' is not used by the path");
                return null;
            }
        }
        return Collections.unmodifiableMap(used);
    }

    private void error(Element element, GeneratedClass generated, String message) {
        generated.invalidate();
        context.error(element, generated.originName() + ": " + message);
    }

    private static final class Operation {
        final NavigatorPlan.Kind kind;
        final String annotation;
        final String expression;
        final boolean ensure;
        final boolean ifParentPresent;
        final boolean ifAbsent;

        Operation(NavigatorPlan.Kind kind, String annotation, String expression,
                  boolean ensure, boolean ifParentPresent, boolean ifAbsent) {
            this.kind = kind;
            this.annotation = annotation;
            this.expression = expression;
            this.ensure = ensure;
            this.ifParentPresent = ifParentPresent;
            this.ifAbsent = ifAbsent;
        }
    }
}
