package org.sjf4j.asm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class loader for runtime-generated ASM compiled-path classes.
 * <p>
 * A loader is cached per parent class loader so generated classes can resolve
 * the same application types as the root object while keeping generated classes
 * isolated from SJF4J's own classes.
 */
public class AsmClassLoader extends ClassLoader {

    private AsmClassLoader(ClassLoader parent) {
        super(parent);
    }

    private static final Map<ClassLoader, AsmClassLoader> CACHE = new ConcurrentHashMap<>();

    private static final AsmClassLoader BOOTSTRAP = new AsmClassLoader(null);

    public static AsmClassLoader of(ClassLoader parent) {
        if (parent == null) return BOOTSTRAP;
        return CACHE.computeIfAbsent(parent, AsmClassLoader::new);
    }

    public Class<?> defineClazz(String name, byte[] b) {
        return defineClass(name, b, 0, b.length);
    }

}
