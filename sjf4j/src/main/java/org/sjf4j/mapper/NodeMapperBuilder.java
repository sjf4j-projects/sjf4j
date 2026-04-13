package org.sjf4j.mapper;

import org.sjf4j.Sjf4j;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.FacadeFactory;
import org.sjf4j.facade.NodeConverter;
import org.sjf4j.facade.NodeFacade;
import org.sjf4j.facade.simple.SimpleNodeFacade;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.JsonPointer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Builder for path-driven object graph mapping.
 *
 * <p>The builder first deep-converts the source object into the target type,
 * then applies declared actions in registration order.
 */
public final class NodeMapperBuilder<S, T> {

    /**
     * Computes a target value from the source root object, the matched target
     * parent container, and the current target value.
     */
    @FunctionalInterface
    public interface ComputeFunction<S> {
        Object apply(S root, Object parent, Object current);
    }

    private final Class<S> sourceType;
    private final Class<T> targetType;
    private final List<MappingAction<S, T>> actions = new ArrayList<>();
    private final List<NodeMapper<?, ?>> nestedMappers = new ArrayList<>();

    /**
     * Creates a builder for the given source and target types.
     */
    public NodeMapperBuilder(Class<S> sourceType, Class<T> targetType) {
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
        this.targetType = Objects.requireNonNull(targetType, "targetType");
    }

    /**
     * Copies a single source path value into a single target path.
     */
    public NodeMapperBuilder<S, T> copy(String targetPath, String sourcePath) {
        return _addCopyAction(targetPath, sourcePath, false);
    }

    /**
     * Copies a single source path value and creates missing target containers.
     */
    public NodeMapperBuilder<S, T> ensureCopy(String targetPath, String sourcePath) {
        return _addCopyAction(targetPath, sourcePath, true);
    }

    /**
     * Writes a fixed value to a single target path.
     */
    public NodeMapperBuilder<S, T> value(String targetPath, Object value) {
        return _addValueAction(targetPath, value, false);
    }

    /**
     * Writes a fixed value to a single target path and creates missing target
     * containers.
     */
    public NodeMapperBuilder<S, T> ensureValue(String targetPath, Object value) {
        return _addValueAction(targetPath, value, true);
    }

    /**
     * Computes target values from the source root object only.
     *
     * <p>Use this overload when the mapped value depends only on the source
     * object graph and not on current target state.
     */
    public NodeMapperBuilder<S, T> compute(String multiPath, Function<S, Object> computer) {
        Objects.requireNonNull(computer, "computer");
        return _addComputeAction(multiPath, false, true,
                (root, parent, current) -> computer.apply(root));
    }

    /**
     * Computes target values from source root, target parent container, and
     * current target value.
     *
     * <p>Single target paths and multi target paths are both supported.
     */
    public NodeMapperBuilder<S, T> compute(String multiPath, ComputeFunction<S> computer) {
        return _addComputeAction(multiPath, false, true, computer);
    }

    /**
     * Computes a single target-path value from the source root object only,
     * creating missing target containers when needed.
     */
    public NodeMapperBuilder<S, T> ensureCompute(String targetPath, Function<S, Object> computer) {
        Objects.requireNonNull(computer, "computer");
        return _addComputeAction(targetPath, true, false,
                (root, parent, current) -> computer.apply(root));
    }

    /**
     * Registers another mapper as an exact nested converter.
     *
     * <p>The nested mapper can then participate in field, element, and nested
     * object conversion when matching source/target types are encountered.
     */
    public NodeMapperBuilder<S, T> with(NodeMapper<?, ?> nestedMapper) {
        Objects.requireNonNull(nestedMapper, "nestedMapper");
        nestedMappers.add(nestedMapper);
        return this;
    }

    /**
     * Builds an immutable mapper that applies actions in declaration order.
     */
    public NodeMapper<S, T> build() {
        final NodeFacade facade = _buildFacade();
        @SuppressWarnings("unchecked")
        final MappingAction<S, T>[] builtActions = actions.toArray(new MappingAction[0]);

        return new NodeMapper<S, T>() {
            @Override
            public Class<S> sourceType() {
                return sourceType;
            }

            @Override
            public Class<T> targetType() {
                return targetType;
            }

            @Override
            public T map(S source) {
                if (source == null) return null;
                T target = targetType.cast(facade.readNode(source, targetType, true));
                for (int i = 0; i < builtActions.length; i++) {
                    builtActions[i].apply(source, target);
                }
                return target;
            }
        };
    }

    private NodeFacade _buildFacade() {
        if (nestedMappers.isEmpty()) {
            return FacadeFactory.defaultNodeFacade();
        }
        NodeConverter<?, ?>[] converters = new NodeConverter[nestedMappers.size()];
        for (int i = 0; i < nestedMappers.size(); i++) {
            NodeMapper<?, ?> nestedMapper = nestedMappers.get(i);
            if (nestedMapper.sourceType() == sourceType && nestedMapper.targetType() == targetType) {
                throw new JsonException("with() does not support nested mapper with same source/target types: '" +
                        sourceType.getName() + "' -> '" + targetType.getName() + "'");
            }
            converters[i] = _toConverter(nestedMapper);
        }
        return new SimpleNodeFacade(converters);
    }

    @SuppressWarnings("unchecked")
    private static NodeConverter<?, ?> _toConverter(NodeMapper<?, ?> nestedMapper) {
        NodeMapper<Object, Object> mapper = (NodeMapper<Object, Object>) nestedMapper;
        return new NodeConverter<Object, Object>() {
            @Override
            public Class<Object> sourceType() {
                return mapper.sourceType();
            }

            @Override
            public Class<Object> targetType() {
                return mapper.targetType();
            }

            @Override
            public Object convert(Object source) {
                return mapper.map(source);
            }
        };
    }

    private NodeMapperBuilder<S, T> _addCopyAction(String targetPath, String sourcePath, boolean ensure) {
        String opName = ensure ? "ensureCopy()" : "copy()";
        JsonPath compiledTargetPath = _requireSingleTargetPath(targetPath, opName);
        JsonPath compiledSourcePath = _requireSingleSourcePath(sourcePath, opName, compiledTargetPath);
        actions.add(new CopyAction<>(compiledSourcePath, compiledTargetPath, ensure));
        return this;
    }

    private NodeMapperBuilder<S, T> _addValueAction(String targetPath, Object value, boolean ensure) {
        String opName = ensure ? "ensureValue()" : "value()";
        actions.add(new ValueAction<>(_requireSingleTargetPath(targetPath, opName), value, ensure));
        return this;
    }

    private NodeMapperBuilder<S, T> _addComputeAction(String targetPath,
                                                      boolean ensure,
                                                      boolean allowMulti,
                                                      ComputeFunction<S> computer) {
        Objects.requireNonNull(computer, "computer");
        String opName = ensure ? "ensureCompute()" : "compute()";
        JsonPath compiledTargetPath = allowMulti ? _compilePath(targetPath) : _requireSingleTargetPath(targetPath, opName);
        actions.add(new ComputeAction<>(compiledTargetPath, computer, ensure));
        return this;
    }

    private static JsonPath _requireSingleTargetPath(String targetPath, String opName) {
        JsonPath compiledTargetPath = _compilePath(targetPath);
        if (!compiledTargetPath.isSingle()) {
            throw new JsonException(opName + " does not support multi target path: target='" + compiledTargetPath + "'");
        }
        return compiledTargetPath;
    }

    private static JsonPath _requireSingleSourcePath(String sourcePath, String opName, JsonPath compiledTargetPath) {
        JsonPath compiledSourcePath = _compilePath(sourcePath);
        if (!compiledSourcePath.isSingle()) {
            throw new JsonException(opName + " does not support multi source path: source='" +
                    compiledSourcePath + "', target='" + compiledTargetPath + "'");
        }
        return compiledSourcePath;
    }

    private static JsonPath _compilePath(String path) {
        Objects.requireNonNull(path, "path");
        String expr = path.trim();
        if (expr.isEmpty()) throw new JsonException("path is empty");
        if (expr.startsWith("$") || expr.startsWith("/")) return JsonPath.compile(expr);
        return JsonPath.compile("$." + expr);
    }


    private interface MappingAction<S, T> {
        void apply(S source, T target);
    }

    private static final class CopyAction<S, T> implements MappingAction<S, T> {
        private final JsonPath sourcePath;
        private final JsonPath targetPath;
        private final boolean ensure;

        private CopyAction(JsonPath sourcePath, JsonPath targetPath, boolean ensure) {
            this.sourcePath = sourcePath;
            this.targetPath = targetPath;
            this.ensure = ensure;
        }

        @Override
        public void apply(S source, T target) {
            Object value = sourcePath.getNode(source);
            if (ensure) targetPath.ensurePut(target, value);
            else targetPath.put(target, value);
        }
    }

    private static final class ValueAction<S, T> implements MappingAction<S, T> {
        private final JsonPath targetPath;
        private final Object value;
        private final boolean ensure;

        private ValueAction(JsonPath targetPath, Object value, boolean ensure) {
            this.targetPath = targetPath;
            this.value = value;
            this.ensure = ensure;
        }

        @Override
        public void apply(S source, T target) {
            if (ensure) targetPath.ensurePut(target, value);
            else targetPath.put(target, value);
        }
    }

    private static final class ComputeAction<S, T> implements MappingAction<S, T> {
        private final JsonPath targetPath;
        private final ComputeFunction<S> computer;
        private final boolean ensure;
        private final JsonPointer parentPath;

        private ComputeAction(JsonPath targetPath, ComputeFunction<S> computer, boolean ensure) {
            if (!targetPath.isSingle() && ensure) {
                throw new JsonException("ensureCompute() does not support multi target path: target='" + targetPath + "'");
            }
            this.targetPath = targetPath;
            this.computer = computer;
            this.ensure = ensure;
            this.parentPath = targetPath.isSingle() ? JsonPointer.compile(targetPath.toPointerExpr()).parent() : null;
        }

        @Override
        public void apply(S source, T target) {
            if (targetPath.isSingle()) {
                Object parent = parentPath == null ? null : parentPath.getNode(target);
                Object current = targetPath.getNode(target);
                Object value = computer.apply(source, parent, current);
                if (ensure) targetPath.ensurePut(target, value);
                else targetPath.put(target, value);
                return;
            }
            targetPath.compute(target, (parent, current) -> computer.apply(source, parent, current));
        }
    }
}
