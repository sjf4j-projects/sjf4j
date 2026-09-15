package org.sjf4j.node;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;

/**
 * Cached component and canonical-constructor metadata for a Java record.
 */
public class RecordInfo {
    public final Class<?> clazz;
    public final Constructor<?> compCtor;
    public final MethodHandle compCtorHandle;
    public final int compCount;
    public final String[] compNames;
    public final Class<?>[] compClasses;
    public final Type[] compTypes;

    /**
     * Creates record metadata.
     */
    public RecordInfo(Class<?> clazz, Constructor<?> compCtor, MethodHandle compCtorHandle,
                      int compCount, String[] compNames, Class<?>[] compClasses, Type[] compTypes) {
        this.clazz = clazz;
        this.compCtor = compCtor;
        this.compCtorHandle = compCtorHandle;
        this.compCount = compCount;
        this.compNames = compNames;
        this.compClasses = compClasses;
        this.compTypes = compTypes;
    }
}
