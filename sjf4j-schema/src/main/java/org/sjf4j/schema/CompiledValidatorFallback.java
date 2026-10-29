package org.sjf4j.schema;

import org.sjf4j.annotation.schema.ValidJsonSchema;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves compiled validator fallbacks from the original annotations, rather
 * than embedding a second copy of the JSON Schema into generated Java source.
 */
public final class CompiledValidatorFallback {
    private CompiledValidatorFallback() {}

    public static SchemaPlan plan(Class<?> validator, String method, Class<?> argumentType, int ordinal) {
        try {
            Method declaration = validator.getMethod(method, argumentType);
            ValidJsonSchema schema = declaration.getAnnotation(ValidJsonSchema.class);
            if (schema == null) schema = declaration.getParameters()[0].getAnnotation(ValidJsonSchema.class);
            if (schema != null) {
                if (ordinal != 0) throw new IllegalArgumentException("invalid schema ordinal: " + ordinal);
                return resolve(schema, argumentType, validator.getName() + "/" + method);
            }

            List<Class<?>> hierarchy = new ArrayList<Class<?>>();
            for (Class<?> type = argumentType; type != null && type != Object.class; type = type.getSuperclass()) {
                if (type.isAnnotationPresent(ValidJsonSchema.class)) hierarchy.add(0, type);
            }
            Class<?> owner = hierarchy.get(ordinal);
            return resolve(owner.getAnnotation(ValidJsonSchema.class), owner, owner.getName());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("cannot resolve compiled validator schema", ex);
        }
    }

    private static SchemaPlan resolve(ValidJsonSchema annotation, Class<?> owner, String source) {
        String inline = annotation.value();
        if (!inline.isEmpty()) {
            return new SchemaRegistry().register(URI.create("sjf4j:/" + source + "/"),
                    JsonSchema.fromJson(inline));
        }

        SchemaValidator runtime = new SchemaValidator();
        String ref = annotation.ref().trim();
        if (!ref.isEmpty()) return require(runtime.load(ref), ref);
        SchemaPlan plan = runtime.load(owner.getSimpleName() + ".json");
        if (plan != null) return plan;
        return require(runtime.load(org.sjf4j.util.Strings.toSnakeCase(owner.getSimpleName()) + ".json"),
                owner.getSimpleName());
    }

    private static SchemaPlan require(SchemaPlan plan, String ref) {
        if (plan == null) throw new IllegalStateException("schema not found: " + ref);
        return plan;
    }
}
