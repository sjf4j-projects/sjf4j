package org.sjf4j.processor.path;

import org.sjf4j.NodeKind;
import org.sjf4j.exception.NodeException;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a find expression once, from left to right, into composable typed
 * traversal steps. All supported selectors produce Java traversal code; a
 * dynamically typed node uses OBNT dispatch rather than whole-path fallback.
 */
final class FindCompiler {
    private final ProcessorContext context;
    private final TypeSystem types;
    private boolean allowFilter;

    FindCompiler(ProcessorContext context) {
        this.context = context;
        this.types = context.types;
    }

    FindPlan compile(ExecutableElement method, GeneratedClass generated,
                     String expression, boolean allowFallback) {
        List<? extends VariableElement> parameters = method.getParameters();
        if (parameters.size() != 1) {
            fail(method, generated, parameters.isEmpty()
                    ? "@FindByPath method must have a root parameter"
                    : "@FindByPath does not support path parameters");
            return null;
        }

        TypeMirror returnType = method.getReturnType();
        if (!types.isSameErasure(returnType, types.listType())) {
            fail(method, generated, "@FindByPath return type must be List<T>, but was " + returnType);
            return null;
        }

        TypeMirror elementType = types.listWriteElementType(returnType);
        if (elementType == null) {
            fail(method, generated, "@FindByPath return List element type is not writable: " + returnType);
            return null;
        }

        JsonPath parsed;
        try {
            parsed = JsonPath.parse(expression);
        } catch (NodeException e) {
            fail(method, generated, "Invalid JSON Path value: " + e.getMessage());
            return null;
        }

        PathSegment[] segments = parsed.segments();
        for (int i = 1; i < segments.length; i++) {
            if (segments[i] instanceof PathSegment.Param) {
                fail(method, generated, "@FindByPath does not support path parameters");
                return null;
            }
            if (segments[i] instanceof PathSegment.Append) {
                fail(method, generated, "@FindByPath does not support append segments");
                return null;
            }
            if (segments[i] instanceof PathSegment.Function) {
                fail(method, generated, "@FindByPath does not support function evaluation");
                return null;
            }
        }

        VariableElement root = parameters.get(0);
        allowFilter = allowFallback;
        try {
            FindPlan.Step traversal = compilePath(root.asType(), segments, 1, elementType);
            return new FindPlan(method, root, expression, elementType, traversal);
        } catch (CompileFailure e) {
            fail(method, generated, "@FindByPath path '" + expression + "': " + e.getMessage());
            return null;
        }
    }

    private FindPlan.Step compilePath(TypeMirror owner, PathSegment[] segments, int index,
                                      TypeMirror resultType) {
        if (index == segments.length) {
            if (!types.isObject(resultType) &&
                    !types.isObject(owner) &&
                    !types.isCompileTimeUnknown(owner) &&
                    !types.isAssignableBoxedGeneric(owner, resultType)) {
                throw invalid("result type mismatch: cannot add " + owner +
                        " to List<" + resultType + ">");
            }
            return FindPlan.Step.collect(owner, resultType);
        }

        PathSegment segment = segments[index];
        if (segment instanceof PathSegment.Union) {
            List<FindPlan.Step> alternatives = new ArrayList<>();
            for (PathSegment selection : ((PathSegment.Union) segment).union) {
                alternatives.add(compileSelection(owner, selection, segments, index, resultType, true));
            }
            return new FindPlan.Step(FindPlan.Kind.UNION, segment, index, owner, owner,
                    null, null, alternatives);
        }
        return compileSelection(owner, segment, segments, index, resultType, false);
    }

    private FindPlan.Step compileSelection(TypeMirror owner, PathSegment segment,
                                           PathSegment[] segments, int index,
                                           TypeMirror resultType, boolean optional) {
        if (segment instanceof PathSegment.Name || segment instanceof PathSegment.Index) {
            NodeAccess access;
            if (segment instanceof PathSegment.Name) {
                access = context.access.resolveName(owner, ((PathSegment.Name) segment).name);
                if (access != null && access.kind() == NodeAccess.Kind.MAP &&
                        !types.hasSafeStringPathKey(owner)) {
                    throw invalid("cannot use String path key with Map key type " +
                            types.mapDeclaredKeyType(owner));
                }
            } else {
                access = context.access.resolveIndex(owner);
            }

            if (access == null) {
                // An inapplicable alternative contributes no nodes, but does
                // not invalidate the other union members. Dynamic types still
                // require runtime evaluation because their shape is unknown.
                NodeKind kind = types.nodeKind(owner);
                if (optional && kind != NodeKind.UNKNOWN &&
                        kind != NodeKind.COMPILE_TIME_UNKNOWN) {
                    return FindPlan.Step.empty(owner);
                }
                // A statically known incompatible selector cannot match.
                if (kind != NodeKind.UNKNOWN && kind != NodeKind.COMPILE_TIME_UNKNOWN) {
                    return FindPlan.Step.empty(owner);
                }
                throw unsupported("cannot compile " + segment + " on " + owner);
            }
            if (!access.readable()) {
                throw invalid("property " + segment + " of " + owner + " is not readable");
            }
            TypeMirror value = access.readType();
            FindPlan.Step next = compilePath(value, segments, index + 1, resultType);
            return new FindPlan.Step(FindPlan.Kind.READ, segment, index, owner, value,
                    access, next, null);
        }

        if (segment instanceof PathSegment.Wildcard ||
                segment instanceof PathSegment.Slice ||
                segment instanceof PathSegment.Filter) {
            if (segment instanceof PathSegment.Filter && !allowFilter) {
                throw unsupported("filter predicates require @CompiledNavigator(fallback=true)");
            }

            NodeKind kind = types.nodeKind(owner);
            boolean slice = segment instanceof PathSegment.Slice;
            TypeMirror value = elementType(owner, !slice);

            // An inapplicable selector on a known static shape selects nothing.
            if (value == null) {
                if (kind == NodeKind.UNKNOWN || kind == NodeKind.COMPILE_TIME_UNKNOWN) {
                    throw unsupported("cannot enumerate " + segment + " on " + owner);
                }
                return FindPlan.Step.empty(owner);
            }

            NodeAccess iteration;
            if (kind == NodeKind.OBJECT_MAP || kind == NodeKind.OBJECT_JSON_OBJECT
                    || kind == NodeKind.OBJECT_JOJO) {
                iteration = context.access.resolveDynamicName(owner);
            } else if (kind == NodeKind.ARRAY_LIST || kind == NodeKind.ARRAY_ARRAY
                    || kind == NodeKind.ARRAY_JSON_ARRAY || kind == NodeKind.ARRAY_JAJO
                    || kind == NodeKind.COMPILE_TIME_UNKNOWN) {
                iteration = context.access.resolveIndex(owner);
            } else {
                // Set and POJO use their dedicated iterator / NodeWalker traversal.
                iteration = null;
            }

            FindPlan.Step next = compilePath(value, segments, index + 1, resultType);
            FindPlan.Kind selection = segment instanceof PathSegment.Wildcard
                    ? FindPlan.Kind.WILDCARD
                    : segment instanceof PathSegment.Filter ? FindPlan.Kind.FILTER
                    : FindPlan.Kind.SLICE;
            return new FindPlan.Step(selection, segment, index, owner, value, iteration, next, null);
        }

        if (segment instanceof PathSegment.Descendant) {
            if (index + 1 >= segments.length) {
                throw invalid("descendant '..' needs a following selector");
            }
            // Descendants may cross arbitrary OBNT representations. Continue
            // using Object/NodeWalker without discarding the typed prefix.
            FindPlan.Step next = compilePath(types.objectType(), segments, index + 1, resultType);
            return new FindPlan.Step(FindPlan.Kind.DESCENDANT, segment, index,
                    owner, types.objectType(), null, next, null);
        }
        throw unsupported("unsupported path segment " + segment);
    }

    private TypeMirror elementType(TypeMirror type, boolean allowObject) {
        type = types.concrete(type);
        NodeKind kind = types.nodeKind(type);
        if (allowObject && (kind == NodeKind.OBJECT_MAP
                || kind == NodeKind.OBJECT_JSON_OBJECT || kind == NodeKind.OBJECT_JOJO
                || kind == NodeKind.OBJECT_POJO)) {
            return kind == NodeKind.OBJECT_MAP
                    ? types.mapReadValueType(type) : types.objectType();
        }
        if (type.getKind() == TypeKind.ARRAY) {
            return types.concrete(((ArrayType) type).getComponentType());
        }
        if (kind == NodeKind.ARRAY_LIST) return types.listReadElementType(type);
        if (kind == NodeKind.ARRAY_SET) return allowObject ? types.setReadElementType(type) : null;
        if (kind == NodeKind.ARRAY_JSON_ARRAY || kind == NodeKind.ARRAY_JAJO
                || kind == NodeKind.COMPILE_TIME_UNKNOWN) {
            return types.objectType();
        }
        // Filters can also evaluate one scalar/current node (runtime behavior).
        if (allowObject && allowFilter && (kind.isValue())) return type;
        return null;
    }

    private static CompileFailure unsupported(String detail) {
        return new CompileFailure(detail, true);
    }

    private static CompileFailure invalid(String detail) {
        return new CompileFailure(detail, false);
    }

    private void fail(ExecutableElement method, GeneratedClass generated, String message) {
        generated.invalidate();
        context.error(method, generated.originName() + ": " + message);
    }

    private static final class CompileFailure extends RuntimeException {
        final boolean recoverable;

        CompileFailure(String message, boolean recoverable) {
            super(message);
            this.recoverable = recoverable;
        }
    }
}
