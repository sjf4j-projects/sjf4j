package org.sjf4j.meta;

import org.sjf4j.exception.BindingException;

/**
 * Lazily resolves a fixed class's metadata from the global type registry.
 *
 * <p>Resolution is deferred to avoid eager analysis of recursive type graphs.
 * Cached TypeInfo includes {@link TypeInfo#NONE} for unregistered types.</p>
 */
public final class LazyTypeInfo {

    public final Class<?> clazz;
    private TypeInfo typeInfo;

    public LazyTypeInfo(Class<?> clazz) {
        this.clazz = clazz;
    }

    /**
     * Creates a lazy metadata reference only for types needing registry analysis.
     * Returns null for types that {@link TypeRegistry} always classifies as
     * {@link TypeInfo#NONE}, so callers can keep their scalar/container fast paths.
     */
    public static LazyTypeInfo of(Class<?> clazz) {
        return TypeRegistry.isFastNoneType(clazz) ? null : new LazyTypeInfo(clazz);
    }

    /** Returns the metadata, resolving it at most once in sequential use. */
    public TypeInfo get() {
        TypeInfo ti = typeInfo;
        if (ti == null) {
            ti = TypeRegistry.registerTypeInfo(clazz);
            typeInfo = ti;
        }
        return ti;
    }

    /** Returns POJO metadata for a key-discriminated OneOf target. */
    public PojoInfo requirePojoInfo() {
        PojoInfo pi = get().pojoInfo;
        if (pi == null) {
            throw new BindingException("class '" + clazz.getName() + "' is not a POJO");
        }
        return pi;
    }
}
