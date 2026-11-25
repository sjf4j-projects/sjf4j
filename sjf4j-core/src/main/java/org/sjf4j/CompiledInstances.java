package org.sjf4j;

import org.sjf4j.binding.Binder;
import org.sjf4j.exception.NodeException;
import org.sjf4j.util.Asserts;

import java.lang.reflect.Constructor;

/**
 * Factory for annotation-processor generated implementations of
 * {@code @CompiledNavigator}, {@code @CompiledMapper},
 * {@code @CompiledJdbcMapper}, {@code @CompiledBinder}, and
 * {@code @CompiledValidator} interfaces.
 *
 * <p>Each {@link #create(Class)} invocation constructs a new instance of
 * the generated {@code _Impl} class. The factory does not cache instances
 * or manage their lifecycle; applications may retain or inject instances
 * according to their own requirements.
 */
public final class CompiledInstances {

    /** Suffix used for every standard annotation-processor generated implementation. */
    public static final String IMPL_SUFFIX = "_Impl";

    private CompiledInstances() {}

    /** Creates an independent compiled implementation using compile-time settings. */
    public static <T> T create(Class<T> type) {
        return _instantiate(type, new Class<?>[0]);
    }

    /**
     * Creates a CompiledBinder with a concrete backend Binder.
     * Native parser/generator customization is preserved; compiled binding
     * semantics (including fallback) are independent of the Binder's NodeConfig.
     */
    public static <T> T create(Class<T> type, Binder backendBinder) {
        Asserts.notNull(backendBinder, "backendBinder");
        return _instantiate(type, new Class<?>[] {backendBinder.getClass()}, backendBinder);
    }

    private static <T> T _instantiate(Class<T> type, Class<?>[] parameterTypes, Object... arguments) {
        Asserts.notNull(type, "type");
        Class<?> implClass = _implementationClass(type);
        try {
            return type.cast(implClass.getConstructor(parameterTypes).newInstance(arguments));
        } catch (NoSuchMethodException e) {
            String signature = parameterTypes.length == 0 ? "no arguments" : parameterTypes[0].getName();
            throw new IllegalArgumentException("no constructor accepting " + signature
                    + " in generated implementation '" + implClass.getName() + "'", e);
        } catch (ReflectiveOperationException e) {
            throw new NodeException("failed to instantiate generated implementation '"
                    + implClass.getName() + "' for interface '" + type.getName() + "'", e);
        }
    }

    private static Class<?> _implementationClass(Class<?> type) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException("expected interface type, but was '" + type.getName() + "'");
        }

        String implName = type.getName() + IMPL_SUFFIX;
        Class<?> implClass;
        try {
            implClass = Class.forName(implName, true, type.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new NodeException("cannot find generated implementation '" + implName
                    + "' for interface '" + type.getName()
                    + "'; ensure a supported @Compiled annotation is present and annotation processing is enabled", e);
        } catch (LinkageError e) {
            throw new NodeException("failed to load generated implementation '" + implName
                    + "' for interface '" + type.getName() + "'", e);
        }

        if (!type.isAssignableFrom(implClass)) {
            throw new NodeException("generated implementation '" + implName
                    + "' does not implement '" + type.getName() + "'");
        }

        return implClass;
    }

}
