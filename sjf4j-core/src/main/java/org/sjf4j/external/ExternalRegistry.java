package org.sjf4j.external;

import org.sjf4j.NodeKind;
import org.sjf4j.annotation.node.NodeRegistry;
import org.sjf4j.exception.NodeException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Setup-time registry for externally represented JSON node adapters.
 *
 * <p>Existing SPI discovery remains independent of explicit
 * {@link NodeRegistry} interfaces. Register custom adapters before metadata
 * for their types is analyzed.
 */
public final class ExternalRegistry {
    private ExternalRegistry() {}

    private static final Map<Class<?>, ExternalNode<?>> EXTERNAL_NODES = new ConcurrentHashMap<>();
    private static final Set<Class<?>> REGISTERED_HOLDERS = new HashSet<>();

    static {
        ClassLoader loader = ExternalProvider.class.getClassLoader();
        for (ExternalProvider provider : ServiceLoader.load(ExternalProvider.class, loader)) {
            ExternalNode<?> adapter = provider.externalNode();
            if (adapter != null) register(adapter);
        }
    }

    /** Registers a concrete adapter for its declared external root type. */
    public static void register(ExternalNode<?> adapter) {
        if (adapter == null) throw new NodeException("external adapter must not be null");
        Class<?> type = adapter.nodeType();
        if (type == null) throw new NodeException("external node type must not be null");
        if (type == Object.class || NodeKind.plainOf(type) != NodeKind.UNKNOWN) {
            throw new NodeException("external node type must not be a native OBNT type: '" + type.getName() + "'");
        }
        if (EXTERNAL_NODES.putIfAbsent(type, adapter) != null) {
            throw new NodeException("external node already registered for type '" + type.getName() + "'");
        }
    }

    /**
     * Registers static ExternalNode<N> fields and zero-argument factories
     * declared in an annotated registry interface. Codec declarations are
     * handled separately by ValueRegistry.
     */
    public static synchronized void registerAnnotated(Class<?> registry) {
        if (registry == null || !registry.isInterface()
                || !registry.isAnnotationPresent(NodeRegistry.class)) {
            throw new NodeException("expected @NodeRegistry interface: "
                    + (registry == null ? "null" : registry.getName()));
        }
        if (REGISTERED_HOLDERS.contains(registry)) return;

        Field[] fields = registry.getDeclaredFields();
        Method[] methods = registry.getDeclaredMethods();
        Arrays.sort(fields, Comparator.comparing(Field::getName));
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        for (Field field : fields) {
            if (!ExternalNode.class.isAssignableFrom(field.getType())) continue;
            int flags = field.getModifiers();
            if (!Modifier.isPublic(flags) || !Modifier.isStatic(flags)
                    || !Modifier.isFinal(flags)) {
                throw new NodeException("external node field must be public static final: " + field);
            }
            ExternalNode<?> adapter;
            try {
                adapter = (ExternalNode<?>) field.get(null);
            } catch (IllegalAccessException e) {
                throw new NodeException("cannot access external node field: " + field, e);
            }
            registerDeclared(field.getGenericType(), adapter, field.toString());
        }
        for (Method method : methods) {
            if (!ExternalNode.class.isAssignableFrom(method.getReturnType())) continue;
            int flags = method.getModifiers();
            if (!Modifier.isPublic(flags) || !Modifier.isStatic(flags)
                    || method.getParameterCount() != 0) {
                throw new NodeException("external node factory must be public static and no-arg: " + method);
            }
            ExternalNode<?> adapter;
            try {
                adapter = (ExternalNode<?>) method.invoke(null);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new NodeException("cannot invoke external node factory: " + method, e);
            }
            registerDeclared(method.getGenericReturnType(), adapter, method.toString());
        }
        REGISTERED_HOLDERS.add(registry);
    }

    private static void registerDeclared(Type declared, ExternalNode<?> adapter, String member) {
        if (!(declared instanceof ParameterizedType)
                || ((ParameterizedType) declared).getActualTypeArguments().length != 1
                || !(((ParameterizedType) declared).getActualTypeArguments()[0] instanceof Class)) {
            throw new NodeException("external node declaration must specify concrete N: " + member);
        }
        Class<?> declaredType =
                (Class<?>) ((ParameterizedType) declared).getActualTypeArguments()[0];
        if (adapter == null || adapter.nodeType() != declaredType) {
            throw new NodeException("external node type does not match declaration: " + member);
        }
        register(adapter);
    }

    /**
     * Returns the adapter with the most-specific discovered root type that accepts
     * {@code nodeType}, or {@code null} when no adapter is available.
     */
    public static ExternalNode<?> resolve(Class<?> nodeType) {
        Class<?> resolvedType = null;
        ExternalNode<?> resolved = null;
        for (Map.Entry<Class<?>, ExternalNode<?>> entry : EXTERNAL_NODES.entrySet()) {
            Class<?> candidateType = entry.getKey();
            if (!candidateType.isAssignableFrom(nodeType)) continue;
            if (resolvedType == null || resolvedType.isAssignableFrom(candidateType)) {
                resolvedType = candidateType;
                resolved = entry.getValue();
            }
        }
        return resolved;
    }
}
