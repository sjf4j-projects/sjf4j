package org.sjf4j.mapper;

import org.sjf4j.compiled.CompiledPath;
import org.sjf4j.compiled.FallbackCompiledPath;
import org.sjf4j.exception.JsonException;
import org.sjf4j.path.JsonPath;

/**
 * Pre-compiled mapping action that replaces per-call {@link JsonPath} segment
 * interpretation with {@link CompiledPath} accessors.
 *
 * <p>All actions share a single type so the action-loop dispatch remains
 * monomorphic. Each instance stores only the fields needed for its kind.
 *
 * <p>This class is intentionally package-private; users interact through
 * {@link NodeMapperBuilder#buildCompiled()}.
 */
final class CompiledAction<S, T> {

    static final int COPY = 0;
    static final int VALUE = 1;
    static final int COMPUTE = 2;
    static final int WILDCARD_COMPUTE = 3;

    final int kind;
    final CompiledPath<Object, Object> sourcePath; // COPY only
    final CompiledPath<Object, Object> targetPath; // COPY, VALUE, COMPUTE
    final CompiledPath<Object, Object> parentPath; // COMPUTE only
    final Object fixedValue;                         // VALUE only
    final NodeMapperBuilder.ComputeFunction<S> computer; // COMPUTE, WILDCARD_COMPUTE
    final boolean ensure;                            // all except WILDCARD_COMPUTE
    final JsonPath wildcardPath;                     // WILDCARD_COMPUTE only

    private CompiledAction(int kind,
                           CompiledPath<Object, Object> sourcePath,
                           CompiledPath<Object, Object> targetPath,
                           CompiledPath<Object, Object> parentPath,
                           Object fixedValue,
                           NodeMapperBuilder.ComputeFunction<S> computer,
                           boolean ensure,
                           JsonPath wildcardPath) {
        this.kind = kind;
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
        this.parentPath = parentPath;
        this.fixedValue = fixedValue;
        this.computer = computer;
        this.ensure = ensure;
        this.wildcardPath = wildcardPath;
    }

    // ---- factory methods --------------------------------------------------

    static <S, T> CompiledAction<S, T> copy(
            CompiledPath<Object, Object> sourcePath,
            CompiledPath<Object, Object> targetPath,
            boolean ensure) {
        return new CompiledAction<>(COPY, sourcePath, targetPath, null, null, null, ensure, null);
    }

    static <S, T> CompiledAction<S, T> value(
            CompiledPath<Object, Object> targetPath,
            Object fixedValue,
            boolean ensure) {
        return new CompiledAction<>(VALUE, null, targetPath, null, fixedValue, null, ensure, null);
    }

    static <S, T> CompiledAction<S, T> compute(
            CompiledPath<Object, Object> targetPath,
            CompiledPath<Object, Object> parentPath,
            NodeMapperBuilder.ComputeFunction<S> computer,
            boolean ensure) {
        return new CompiledAction<>(COMPUTE, null, targetPath, parentPath, null, computer, ensure, null);
    }

    static <S, T> CompiledAction<S, T> wildcardCompute(
            JsonPath path,
            NodeMapperBuilder.ComputeFunction<S> computer) {
        return new CompiledAction<>(WILDCARD_COMPUTE, null, null, null, null, computer, false, path);
    }

    // ---- execution --------------------------------------------------------

    void apply(S source, T target) {
        switch (kind) {
            case COPY: {
                Object value = sourcePath.get(source);
                if (ensure) targetPath.ensurePut(target, value);
                else targetPath.put(target, value);
                break;
            }
            case VALUE: {
                if (ensure) targetPath.ensurePut(target, fixedValue);
                else targetPath.put(target, fixedValue);
                break;
            }
            case COMPUTE: {
                Object parent = parentPath != null ? parentPath.get(target) : target;
                Object current = targetPath.get(target);
                Object value = computer.apply(source, parent, current);
                if (ensure) targetPath.ensurePut(target, value);
                else targetPath.put(target, value);
                break;
            }
            case WILDCARD_COMPUTE: {
                wildcardPath.compute(target, (parent, current) ->
                        computer.apply(source, parent, current));
                break;
            }
        }
    }

    // ---- compilation helper -----------------------------------------------

    /**
     * Converts a parsed {@link JsonPath} into a {@link CompiledPath}.
     *
     * <p>Attempts bytecode compilation via {@link CompiledPath#compile}
     * when an ASM compiler is present on the classpath; falls back to
     * {@link FallbackCompiledPath} (reflective) otherwise.
     * Root-only paths (length &lt; 2) skip the ASM attempt directly.
     */
    @SuppressWarnings("unchecked")
    static CompiledPath<Object, Object> compilePath(JsonPath path, Class<?> rootType) {
        if (path.length() < 2) {
            return new FallbackCompiledPath<>(path, (Class) rootType, Object.class);
        }
        try {
            return (CompiledPath<Object, Object>) CompiledPath.compile(
                    path.toExpr(), (Class) rootType, Object.class);
        } catch (JsonException e) {
            return new FallbackCompiledPath<>(path, (Class) rootType, Object.class);
        }
    }
}
