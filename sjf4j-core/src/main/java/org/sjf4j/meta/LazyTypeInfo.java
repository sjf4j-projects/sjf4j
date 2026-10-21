package org.sjf4j.meta;

/**
 * Lazily resolves cached metadata for an OneOf mapping target.
 *
 * <p>Subtype metadata must not be analyzed while constructing OneOfInfo:
 * recursive polymorphic object graphs can refer back to the declaring type.</p>
 */
public final class LazyTypeInfo {

    public final Class<?> clazz;
    private volatile TypeInfo typeInfo;

    public LazyTypeInfo(Class<?> clazz) {
        this.clazz = clazz;
    }

    /** Resolves a key-discriminated POJO subtype once, then reuses its metadata. */
    public PojoInfo pojoInfo() {
        TypeInfo ti = typeInfo;
        if (ti == null) {
            ti = TypeRegistry.registerTypeInfo(clazz, true);
            typeInfo = ti;
        }
        return ti.pojoInfo;
    }
}
