package org.sjf4j.node;

import org.sjf4j.JsonType;
import org.sjf4j.annotation.node.OneOf;
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
    public final OneOf.Mapping[] mappings;
    public final String key;
    public final String path;
    public final OneOf.Scope scope;
    public final OneOf.OnNoMatch onNoMatch;
    public final boolean fallbackNull;
    public final boolean hasDiscriminator;
    public final boolean keyDiscriminator;
    public final EnumMap<JsonType, Class<?>> byJsonType;
    public final Map<String, Class<?>> byWhen;
    public final JsonPath compiledPath;

    /**
     * Creates metadata from a {@link OneOf} declaration.
     */
    public OneOfInfo(Class<?> clazz, OneOf.Mapping[] mappings, String key,
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
            for (OneOf.Mapping mapping : mappings) {
                for (String when : mapping.when()) {
                    byWhen.put(when, mapping.value());
                }
            }
        } else {
            this.byWhen = null;
            this.byJsonType = new EnumMap<>(JsonType.class);
            for (OneOf.Mapping mapping : mappings) {
                byJsonType.put(JsonType.rawOf(mapping.value()), mapping.value());
            }
        }
    }

    /**
     * Resolves a mapped subtype for a JSON type, or {@code null} when absent.
     */
    public Class<?> matchByJsonType(JsonType jsonType) {
        if (byJsonType == null || jsonType == null) return null;
        return byJsonType.get(jsonType);
    }

    /**
     * Resolves a mapped subtype for a discriminator value, or {@code null} when absent.
     */
    public Class<?> matchByWhen(Object when) {
        if (byWhen == null || when == null) return null;
        return byWhen.get(String.valueOf(when));
    }


}
