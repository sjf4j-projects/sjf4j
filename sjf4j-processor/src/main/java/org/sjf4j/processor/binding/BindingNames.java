package org.sjf4j.processor.binding;

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

    static String simpleType(TypeMirror type) {
        String erased = type.toString();
        int generic = erased.indexOf('<');
        if (generic >= 0) erased = erased.substring(0, generic);
        int dot = Math.max(erased.lastIndexOf('.'), erased.lastIndexOf('$'));
        return erased.substring(dot + 1).replace("[]", "Array");
    }

    static String fieldType(TypeMirror type) {
        return upperSnake(simpleType(type));
    }

    static String upperSnake(String text) {
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                if (name.length() != 0 && name.charAt(name.length() - 1) != '_') name.append('_');
                continue;
            }
            if (Character.isUpperCase(c) && i > 0
                    && name.length() > 0 && name.charAt(name.length() - 1) != '_'
                    && (Character.isLowerCase(text.charAt(i - 1))
                        || Character.isDigit(text.charAt(i - 1)))) {
                name.append('_');
            }
            name.append(Character.toUpperCase(c));
        }
        return name.length() == 0 ? "VALUE" : name.toString();
    }

    static String lowerType(TypeMirror type) {
        String simple = simpleType(type);
        return simple.isEmpty() ? "value"
                : Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }
}
