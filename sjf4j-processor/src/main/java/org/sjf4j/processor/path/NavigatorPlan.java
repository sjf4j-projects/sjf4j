package org.sjf4j.processor.path;

import org.sjf4j.path.JsonPath;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import java.util.Map;

/**
 * Fully analyzed navigator method, before static node access compilation.
 * The plan is independent of the Java source emitter.
 */
final class NavigatorPlan {
    enum Kind { GET, PUT, FIND }

    final Kind kind;
    final ExecutableElement method;
    final String expression;
    final boolean allowFallback;
    final Get get;
    final Put put;

    private NavigatorPlan(Kind kind, ExecutableElement method, String expression,
                          boolean allowFallback, Get get, Put put) {
        this.kind = kind;
        this.method = method;
        this.expression = expression;
        this.allowFallback = allowFallback;
        this.get = get;
        this.put = put;
    }

    static NavigatorPlan get(Get get) {
        return new NavigatorPlan(Kind.GET, get.method, null, false, get, null);
    }

    static NavigatorPlan put(Put put) {
        return new NavigatorPlan(Kind.PUT, put.method, null, false, null, put);
    }

    static NavigatorPlan find(ExecutableElement method, String expression, boolean fallback) {
        return new NavigatorPlan(Kind.FIND, method, expression, fallback, null, null);
    }

    static final class Get {
        final ExecutableElement method;
        final VariableElement root;
        final JsonPath path;
        final Map<String, VariableElement> pathParameters;

        Get(ExecutableElement method, VariableElement root, JsonPath path,
            Map<String, VariableElement> pathParameters) {
            this.method = method;
            this.root = root;
            this.path = path;
            this.pathParameters = pathParameters;
        }
    }

    static final class Put {
        final ExecutableElement method;
        final VariableElement root;
        final VariableElement value;
        final JsonPath path;
        final Map<String, VariableElement> pathParameters;
        final String annotation;
        final boolean ensure;
        final boolean ifParentPresent;
        final boolean ifAbsent;

        Put(ExecutableElement method, VariableElement root, VariableElement value,
            JsonPath path, Map<String, VariableElement> pathParameters, String annotation,
            boolean ensure, boolean ifParentPresent, boolean ifAbsent) {
            this.method = method;
            this.root = root;
            this.value = value;
            this.path = path;
            this.pathParameters = pathParameters;
            this.annotation = annotation;
            this.ensure = ensure;
            this.ifParentPresent = ifParentPresent;
            this.ifAbsent = ifAbsent;
        }
    }
}
