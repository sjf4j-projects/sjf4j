package org.sjf4j.processor.binding;

import org.sjf4j.processor.code.NameAllocator;

import org.sjf4j.processor.code.NameHints;

import org.sjf4j.processor.code.GeneratedNames;

import javax.lang.model.type.TypeMirror;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Names of generated binder members, assigned once and reused by all backend emitters.
 *
 * <p>Semantic roles are private to the compiler and never become Java
 * identifiers. The shared GeneratedNames allocator guarantees collisions are
 * resolved consistently even for similarly named types and properties.</p>
 */
final class BindingNames {

    private final GeneratedNames generated;
    private final Map<BindingValue, Map<String, String>> values = new IdentityHashMap<>();
    private final Map<String, String> enums = new LinkedHashMap<>();
    private final Map<String, String> shared = new HashMap<>();

    BindingNames(GeneratedNames generated) {
        this.generated = generated;
    }

    void primaryHelper(BindingValue value, String suggestion) {
        helper(value, "primary", suggestion);
    }

    String primaryHelper(BindingValue value) {
        return require(value, "helper:primary");
    }

    String preparedName(BindingValue value, int index) {
        return require(value, "field:prepared:" + index);
    }

    private String require(BindingValue value, String role) {
        Map<String, String> owned = values.get(value);
        String allocated = owned == null ? null : owned.get(role);
        if (allocated == null) {
            throw new IllegalStateException("Name not allocated for " + value.type() + ": " + role);
        }
        return allocated;
    }

    String field(BindingValue value, String role, String suggestion) {
        return name(value, "field:" + role, suggestion, false);
    }

    String helper(BindingValue value, String role, String suggestion) {
        return name(value, "helper:" + role, suggestion, true);
    }

    private String name(BindingValue value, String role, String suggestion, boolean method) {
        Map<String, String> owned = values.get(value);
        if (owned == null) {
            owned = new LinkedHashMap<>();
            values.put(value, owned);
        }
        String allocated = owned.get(role);
        if (allocated == null) {
            allocated = method ? generated.helperMethod(suggestion) : generated.field(suggestion);
            owned.put(role, allocated);
        }
        return allocated;
    }

    String enumField(TypeMirror type, String role, String suggestion) {
        return enumName(type, "field:" + role, suggestion, false);
    }

    String enumHelper(TypeMirror type, String role, String suggestion) {
        return enumName(type, "helper:" + role, suggestion, true);
    }

    private String enumName(TypeMirror type, String role, String suggestion, boolean method) {
        String key = type.toString() + ":" + role;
        String allocated = enums.get(key);
        if (allocated == null) {
            allocated = method ? generated.helperMethod(suggestion) : generated.field(suggestion);
            enums.put(key, allocated);
        }
        return allocated;
    }

    String sharedField(String suggestion) {
        String key = "field:" + suggestion;
        String allocated = shared.get(key);
        if (allocated == null) {
            allocated = generated.field(suggestion);
            shared.put(key, allocated);
        }
        return allocated;
    }

    String sharedHelper(String suggestion) {
        String allocated = shared.get(suggestion);
        if (allocated == null) {
            allocated = generated.helperMethod(suggestion);
            shared.put(suggestion, allocated);
        }
        return allocated;
    }

    NameAllocator propertyLocals(String... reserved) {
        // These package roots can occur in generated static expressions.
        return generated.memberSafeHelperLocals(reserved)
                .reserve("java").reserve("javax").reserve("org").reserve("com")
                .reserve("tools").reserve("jakarta");
    }

    static String simpleType(TypeMirror type) {
        return NameHints.simpleType(type);
    }

    static String fieldType(TypeMirror type) {
        return NameHints.upperSnake(simpleType(type));
    }

    static String upperSnake(String text) {
        return NameHints.upperSnake(text);
    }

    static String lowerType(TypeMirror type) {
        return NameHints.lowerCamel(simpleType(type));
    }
}
