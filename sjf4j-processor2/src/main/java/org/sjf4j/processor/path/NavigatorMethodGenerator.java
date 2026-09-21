package org.sjf4j.processor.path;

import org.sjf4j.annotation.path.EnsurePutByPath;
import org.sjf4j.annotation.path.EnsurePutIfAbsentByPath;
import org.sjf4j.annotation.path.FindByPath;
import org.sjf4j.annotation.path.GetByPath;
import org.sjf4j.annotation.path.PutByPath;
import org.sjf4j.annotation.path.PutIfParentPresentByPath;
import org.sjf4j.exception.JsonException;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates one method of a compiled navigator.
 *
 * <p>This class owns navigator method semantics: operation selection, method
 * signature validation, path parsing, path-parameter binding, and operation
 * shape validation.</p>
 *
 * <p>Actual node access and Java source expressions are delegated to
 * {@link PathEmitter}. Multi-target find generation is delegated to
 * {@link FindGenerator}.</p>
 */
public final class NavigatorMethodGenerator {

    private final ProcessorContext context;
    private final PathEmitter pathEmitter;
    private final FindGenerator findGenerator;


    public NavigatorMethodGenerator(ProcessorContext context) {
        this.context = context;
        this.pathEmitter = new PathEmitter(context);
        this.findGenerator = new FindGenerator(context);
    }


    /**
     * Generates one abstract navigator method.
     */
    public void generate(
            ExecutableElement method,
            GeneratedClass generated) {

        if (!method.getTypeParameters().isEmpty()) {
            error(
                    method,
                    generated,
                    "@CompiledNavigator method cannot declare type parameters");
            return;
        }

        Operation operation =
                resolveOperation(method, generated);

        if (operation == null) {
            return;
        }

        switch (operation.kind) {
            case GET:
                generateGet(
                        method,
                        generated,
                        operation);
                return;

            case PUT:
            case PUT_IF_PARENT_PRESENT:
            case ENSURE_PUT:
            case ENSURE_PUT_IF_ABSENT:
                generatePut(
                        method,
                        generated,
                        operation);
                return;

            case FIND:
                findGenerator.generate(
                        method,
                        generated,
                        operation.path,
                        operation.allowFallback);
                return;

            default:
                throw new AssertionError(
                        operation.kind);
        }
    }


    // -------------------------------------------------------------------------
    // Get
    // -------------------------------------------------------------------------

    private void generateGet(
            ExecutableElement method,
            GeneratedClass generated,
            Operation operation) {

        List<? extends VariableElement> parameters =
                method.getParameters();

        if (parameters.isEmpty()) {
            error(
                    method,
                    generated,
                    "@GetByPath method must have a root parameter");
            return;
        }

        if (method.getReturnType().getKind() == TypeKind.VOID) {
            error(
                    method,
                    generated,
                    "@GetByPath method must return the path value");
            return;
        }

        JsonPath path =
                parsePath(
                        method,
                        generated,
                        operation.path,
                        false);

        if (path == null) {
            return;
        }

        if (!path.isSingleGet()) {
            error(
                    method,
                    generated,
                    "@GetByPath supports only Name/Index/Param paths");
            return;
        }

        Map<String, VariableElement> pathParameters =
                resolvePathParameters(
                        method,
                        generated,
                        path,
                        1,
                        parameters.size(),
                        operation.annotation);

        if (pathParameters == null) {
            return;
        }

        GetPlan plan = new GetPlan(
                method,
                parameters.get(0),
                path,
                pathParameters);

        GeneratedClass.Member member =
                pathEmitter.compileGet(plan, generated);

        if (member != null) {
            generated.addMethod(member);
        }
    }


    // -------------------------------------------------------------------------
    // Put
    // -------------------------------------------------------------------------

    private void generatePut(
            ExecutableElement method,
            GeneratedClass generated,
            Operation operation) {

        List<? extends VariableElement> parameters =
                method.getParameters();

        if (parameters.size() < 2) {
            error(
                    method,
                    generated,
                    operation.annotation +
                            " method must have root and value parameters");
            return;
        }

        JsonPath path =
                parsePath(
                        method,
                        generated,
                        operation.path,
                        false);

        if (path == null) {
            return;
        }

        if (!path.isSinglePut()) {
            error(
                    method,
                    generated,
                    operation.annotation +
                            " supports only Name/Index/Param/Append paths");
            return;
        }

        /*
         * Plain put can append only at the final segment.
         *
         * Ensure put is different: intermediate append is meaningful because
         * the appended child can itself become the next parent.
         */
        if (!operation.ensure
                && (path.appendCount() > 1
                || (path.appendCount() != 0
                && !(path.tail() instanceof PathSegment.Append)))) {

            error(
                    method,
                    generated,
                    operation.annotation +
                            " append segment must be the final path segment");
            return;
        }

        VariableElement root =
                parameters.get(0);

        VariableElement value =
                parameters.get(
                        parameters.size() - 1);

        Map<String, VariableElement> pathParameters =
                resolvePathParameters(
                        method,
                        generated,
                        path,
                        1,
                        parameters.size() - 1,
                        operation.annotation);

        if (pathParameters == null) {
            return;
        }

        PutPlan plan = new PutPlan(
                method,
                root,
                value,
                path,
                pathParameters,
                operation.annotation,
                operation.ensure,
                operation.ifParentPresent,
                operation.ifAbsent);

        GeneratedClass.Member member =
                pathEmitter.compilePut(plan, generated);

        if (member != null) {
            generated.addMethod(member);
        }

    }


    // -------------------------------------------------------------------------
    // Operation
    // -------------------------------------------------------------------------

    /**
     * Resolves exactly one navigator operation annotation.
     */
    private Operation resolveOperation(
            ExecutableElement method,
            GeneratedClass generated) {

        Operation operation = null;

        GetByPath get =
                method.getAnnotation(GetByPath.class);

        if (get != null) {
            operation = select(
                    method,
                    generated,
                    operation,
                    new Operation(
                            Kind.GET,
                            "@GetByPath",
                            get.value(),
                            false,
                            false,
                            false,
                            false));
        }

        PutByPath put =
                method.getAnnotation(PutByPath.class);

        if (put != null) {
            operation = select(
                    method,
                    generated,
                    operation,
                    new Operation(
                            Kind.PUT,
                            "@PutByPath",
                            put.value(),
                            false,
                            false,
                            false,
                            false));
        }

        PutIfParentPresentByPath putIfParentPresent =
                method.getAnnotation(
                        PutIfParentPresentByPath.class);

        if (putIfParentPresent != null) {
            operation = select(
                    method,
                    generated,
                    operation,
                    new Operation(
                            Kind.PUT_IF_PARENT_PRESENT,
                            "@PutIfParentPresentByPath",
                            putIfParentPresent.value(),
                            false,
                            false,
                            true,
                            false));
        }

        EnsurePutByPath ensurePut =
                method.getAnnotation(
                        EnsurePutByPath.class);

        if (ensurePut != null) {
            operation = select(
                    method,
                    generated,
                    operation,
                    new Operation(
                            Kind.ENSURE_PUT,
                            "@EnsurePutByPath",
                            ensurePut.value(),
                            false,
                            true,
                            false,
                            false));
        }

        EnsurePutIfAbsentByPath ensurePutIfAbsent =
                method.getAnnotation(
                        EnsurePutIfAbsentByPath.class);

        if (ensurePutIfAbsent != null) {
            operation = select(
                    method,
                    generated,
                    operation,
                    new Operation(
                            Kind.ENSURE_PUT_IF_ABSENT,
                            "@EnsurePutIfAbsentByPath",
                            ensurePutIfAbsent.value(),
                            false,
                            true,
                            false,
                            true));
        }

        FindByPath find =
                method.getAnnotation(FindByPath.class);

        if (find != null) {
            operation = select(
                    method,
                    generated,
                    operation,
                    new Operation(
                            Kind.FIND,
                            "@FindByPath",
                            find.value(),
                            find.allowFallback(),
                            false,
                            false,
                            false));
        }

        if (operation == null) {
            error(
                    method,
                    generated,
                    "@CompiledNavigator abstract method must declare a path operation annotation");
        }

        return operation;
    }


    /**
     * Adds one discovered operation and rejects annotation combinations.
     */
    private Operation select(
            ExecutableElement method,
            GeneratedClass generated,
            Operation previous,
            Operation next) {

        if (previous == null) {
            return next;
        }

        error(
                method,
                generated,
                "Path operation annotations cannot be used together: " +
                        previous.annotation +
                        " and " +
                        next.annotation);

        /*
         * Returning previous lets resolveOperation continue scanning so a
         * method with three conflicting annotations still produces one stable
         * diagnostic instead of changing generation state.
         */
        return previous;
    }


    // -------------------------------------------------------------------------
    // Path
    // -------------------------------------------------------------------------

    /**
     * Parses a path expression.
     *
     * @param allowRoot whether "$" alone is valid
     */
    private JsonPath parsePath(
            ExecutableElement method,
            GeneratedClass generated,
            String expression,
            boolean allowRoot) {

        JsonPath path;

        try {
            path = JsonPath.parse(expression);
        } catch (JsonException e) {
            error(
                    method,
                    generated,
                    "Invalid JSON Path value: " +
                            e.getMessage());
            return null;
        }

        if (!allowRoot && path.length() < 2) {
            error(
                    method,
                    generated,
                    "Invalid JSON Path value: requires a non-root path");
            return null;
        }

        return path;
    }


    /**
     * Binds path parameters such as {name} to method parameters.
     *
     * <p>Only parameters inside [from, to) participate. For put operations this
     * excludes the first root parameter and the final value parameter.</p>
     */
    private Map<String, VariableElement> resolvePathParameters(
            ExecutableElement method,
            GeneratedClass generated,
            JsonPath path,
            int from,
            int to,
            String annotation) {

        List<? extends VariableElement> parameters =
                method.getParameters();

        Map<String, VariableElement> available =
                new LinkedHashMap<String, VariableElement>();

        for (int i = from; i < to; i++) {
            VariableElement parameter =
                    parameters.get(i);

            available.put(
                    parameter.getSimpleName().toString(),
                    parameter);
        }

        Map<String, VariableElement> used =
                new LinkedHashMap<String, VariableElement>();

        PathSegment[] segments =
                path.segments();

        for (int i = 1; i < segments.length; i++) {
            PathSegment segment =
                    segments[i];

            if (!(segment instanceof PathSegment.Param)) {
                continue;
            }

            String name =
                    ((PathSegment.Param) segment).param;

            VariableElement parameter =
                    available.get(name);

            if (parameter == null) {
                error(
                        method,
                        generated,
                        annotation +
                                " path parameter '{" +
                                name +
                                "}' has no matching method parameter");

                return null;
            }

            used.put(name, parameter);
        }

        /*
         * Every intermediate method parameter must actually belong to the path.
         *
         * This catches accidental signature/path drift early:
         *
         *     get(root, String name, int unused)
         */
        for (Map.Entry<String, VariableElement> entry :
                available.entrySet()) {

            if (!used.containsKey(entry.getKey())) {
                error(
                        method,
                        generated,
                        annotation +
                                " method parameter '" +
                                entry.getKey() +
                                "' is not used by the path");

                return null;
            }
        }

        return Collections.unmodifiableMap(used);
    }


    // -------------------------------------------------------------------------
    // Error
    // -------------------------------------------------------------------------

    private void error(
            Element element,
            GeneratedClass generated,
            String message) {

        generated.invalidate();

        context.error(
                element,
                generated.originName() +
                        ": " +
                        message);
    }


    // -------------------------------------------------------------------------
    // Plans
    // -------------------------------------------------------------------------

    /**
     * Input to compiled get emission.
     *
     * <p>PathEmitter enriches this logical plan with concrete node accesses
     * while traversing the path.</p>
     */
    static final class GetPlan {

        final ExecutableElement method;
        final VariableElement root;
        final JsonPath path;
        final Map<String, VariableElement> pathParameters;

        GetPlan(
                ExecutableElement method,
                VariableElement root,
                JsonPath path,
                Map<String, VariableElement> pathParameters) {

            this.method = method;
            this.root = root;
            this.path = path;
            this.pathParameters = pathParameters;
        }
    }


    /**
     * Input to compiled put emission.
     */
    static final class PutPlan {

        final ExecutableElement method;

        final VariableElement root;
        final VariableElement value;

        final JsonPath path;
        final Map<String, VariableElement> pathParameters;

        final String annotation;

        final boolean ensure;
        final boolean ifParentPresent;
        final boolean ifAbsent;


        PutPlan(
                ExecutableElement method,
                VariableElement root,
                VariableElement value,
                JsonPath path,
                Map<String, VariableElement> pathParameters,
                String annotation,
                boolean ensure,
                boolean ifParentPresent,
                boolean ifAbsent) {

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


    // -------------------------------------------------------------------------
    // Operation model
    // -------------------------------------------------------------------------

    private enum Kind {
        GET,
        PUT,
        PUT_IF_PARENT_PRESENT,
        ENSURE_PUT,
        ENSURE_PUT_IF_ABSENT,
        FIND
    }


    private static final class Operation {

        final Kind kind;
        final String annotation;
        final String path;

        final boolean allowFallback;

        final boolean ensure;
        final boolean ifParentPresent;
        final boolean ifAbsent;


        Operation(
                Kind kind,
                String annotation,
                String path,
                boolean allowFallback,
                boolean ensure,
                boolean ifParentPresent,
                boolean ifAbsent) {

            this.kind = kind;
            this.annotation = annotation;
            this.path = path;
            this.allowFallback = allowFallback;
            this.ensure = ensure;
            this.ifParentPresent = ifParentPresent;
            this.ifAbsent = ifAbsent;
        }
    }
}