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
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Setup-time registry for external representation adapters.
 * SPI discovery remains independent of explicit {@link NodeRegistry} declarations.
 */
public final class ExternalRegistry {

    private ExternalRegistry() {}

    private static final Map<Class<?>, ExternalNode<?>> EXTERNAL_NODES = new ConcurrentHashMap<>();

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
        Class<?> nodeType = adapter.nodeType();
        if (nodeType == null) throw new NodeException("external node type must not be null");
        if (nodeType == Object.class || NodeKind.plainOf(nodeType) != NodeKind.UNKNOWN) {
            throw new NodeException("external node type must not be a native OBNT type: '"
                    + nodeType.getName() + "'");
        }
        if (EXTERNAL_NODES.putIfAbsent(nodeType, adapter) != null) {
            throw new NodeException("external node already registered for type '" + nodeType.getName() + "'");
        }
    }

    /**
     * Registers external adapters declared in an {@link NodeRegistry} interface.
     * Only public static final {@code ExternalNode<N>} fields and public static
     * no-argument factory methods are used. Invoke before type metadata is cached.
     */
    public static void registerAnnotated(Class<?> registryClass) {
        if (registryClass == null || !registryClass.isInterface()
                || !registryClass.isAnnotationPresent(NodeRegistry.class)) {
            throw new NodeException("expected @NodeRegistry interface: "
                    + (registryClass == null ? "null" : registryClass.getName()));
        }

        Field[] fields = registryClass.getDeclaredFields();
        Method[] methods = registryClass.getDeclaredMethods();
        Arrays.sort(fields, Comparator.comparing(Field::getName));
        Arrays.sort(methods, Comparator.comparing(Method::getName));

        for (Field field : fields) {
            if (!ExternalNode.class.isAssignableFrom(field.getType())) continue;
            int flags = field.getModifiers();
            if (!Modifier.isPublic(flags) || !Modifier.isStatic(flags)
                    || !Modifier.isFinal(flags)) {
                throw new NodeException("external node field must be public static final: " + field);
            }
            Class<?> declaredType = declaredNodeType(field.getGenericType(), field.toString());
            try {
                registerDeclared((ExternalNode<?>) field.get(null), declaredType, field.toString());
            } catch (IllegalAccessException e) {
                throw new NodeException("cannot access external node field: " + field, e);
            }
        }
        for (Method method : methods) {
            if (!ExternalNode.class.isAssignableFrom(method.getReturnType())) continue;
            int flags = method.getModifiers();
            if (!Modifier.isPublic(flags) || !Modifier.isStatic(flags)
                    || method.getParameterCount() != 0) {
                throw new NodeException("external node factory must be public static, no-arg: " + method);
            }
            Class<?> declaredType = declaredNodeType(method.getGenericReturnType(), method.toString());
            try {
                registerDeclared((ExternalNode<?>) method.invoke(null), declaredType, method.toString());
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new NodeException("cannot invoke external node factory: " + method, e);
            }
        }
    }

    private static Class<?> declaredNodeType(Type declaration, String member) {
        if (!(declaration instanceof ParameterizedType)) {
            throw new NodeException("external node declaration must specify concrete N: " + member);
        }
        Type[] args = ((ParameterizedType) declaration).getActualTypeArguments();
        if (args.length != 1 || !(args[0] instanceof Class)) {
            throw new NodeException("external node declaration must specify concrete N: " + member);
        }
        return (Class<?>) args[0];
    }

    private static void registerDeclared(ExternalNode<?> adapter, Class<?> type, String member) {
        if (adapter == null || adapter.nodeType() != type) {
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
            if (!candidateType.isAssignableFrom(nodeType)) {
                continue;
            }
            if (resolvedType == null || resolvedType.isAssignableFrom(candidateType)) {
                resolvedType = candidateType;
                resolved = entry.getValue();
            }
        }
        return resolved;
    }


}
