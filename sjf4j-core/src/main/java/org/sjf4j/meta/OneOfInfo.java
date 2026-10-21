package org.sjf4j.meta;

import org.sjf4j.JsonType;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.OneOfMapping;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.binding.BackendCache;
import org.sjf4j.path.JsonPath;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Cached resolution metadata for an {@link OneOf} polymorphic type.
 *
 * <p>Mappings are selected by discriminator value when one is configured, or
 * by JSON type otherwise.</p>
 */
public class OneOfInfo {
    public final Class<?> clazz;
    public final OneOfMapping[] mappings;
    public final String key;
    public final String path;
    public final OneOf.Scope scope;
    public final OneOf.OnNoMatch onNoMatch;
    public final boolean fallbackNull;
    public final boolean hasDiscriminator;
    public final boolean keyDiscriminator;
    public final EnumMap<JsonType, LazyTypeInfo> byJsonType;
    public final Map<String, LazyTypeInfo> byWhen;
    public final JsonPath compiledPath;
    public final BackendCache[] backendCache = new BackendCache[Backend.values().length];

    /**
     * Creates metadata from a {@link OneOf} declaration.
     */
    public OneOfInfo(Class<?> clazz, OneOfMapping[] mappings, String key,
                     String path, OneOf.Scope scope, OneOf.OnNoMatch onNoMatch) {
        this.clazz = clazz;
        this.mappings = mappings;
        this.key = key;
        this.path = path;
        this.scope = scope;
        this.onNoMatch = onNoMatch;
        this.fallbackNull = onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL;
        this.compiledPath = path.isEmpty() ? null : JsonPath.parse(path);
        this.hasDiscriminator = !key.isEmpty() || !path.isEmpty();
        this.keyDiscriminator = !key.isEmpty();
        if (hasDiscriminator) {
            this.byJsonType = null;
            this.byWhen = new HashMap<>();
            Map<Class<?>, LazyTypeInfo> targets = new HashMap<>();
            for (OneOfMapping mapping : mappings) {
                LazyTypeInfo target = targets.computeIfAbsent(mapping.type(), LazyTypeInfo::new);
                for (String when : mapping.when()) {
                    byWhen.put(when, target);
                }
            }
        } else {
            this.byWhen = null;
            this.byJsonType = new EnumMap<>(JsonType.class);
            for (OneOfMapping mapping : mappings) {
                byJsonType.put(JsonType.rawOf(mapping.type()), new LazyTypeInfo(mapping.type()));
            }
        }
    }

    /**
     * Resolves a mapped subtype for a JSON type, or {@code null} when absent.
     */
    public Class<?> matchByJsonType(JsonType jsonType) {
        LazyTypeInfo target = lazyTypeInfoByJsonType(jsonType);
        return target == null ? null : target.clazz;
    }

    /** Returns the cached subtype holder for a JSON shape. */
    public LazyTypeInfo lazyTypeInfoByJsonType(JsonType jsonType) {
        if (byJsonType == null || jsonType == null) return null;
        return byJsonType.get(jsonType);
    }

    /**
     * Resolves a mapped subtype for a discriminator value, or {@code null} when absent.
     */
    public Class<?> matchByWhen(Object when) {
        LazyTypeInfo target = lazyTypeInfoByWhen(when);
        return target == null ? null : target.clazz;
    }

    /** Resolves the reusable subtype holder without analyzing its metadata. */
    public LazyTypeInfo lazyTypeInfoByWhen(Object when) {
        if (byWhen == null || when == null) return null;
        return byWhen.get(String.valueOf(when));
    }

    /** Returns the reusable backend cache for this discriminator mapping. */
    public BackendCache backendCache(Backend backend) {
        int index = backend.ordinal();
        BackendCache cache = backendCache[index];
        if (cache == null) {
            cache = new BackendCache();
            backendCache[index] = cache;
        }
        return cache;
    }
}
