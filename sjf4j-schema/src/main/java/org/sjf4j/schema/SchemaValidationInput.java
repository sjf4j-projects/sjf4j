package org.sjf4j.schema;

import org.sjf4j.JsonType;
import org.sjf4j.Nodes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional Java-object projection for compiled validators.
 *
 * <p>When nullAsMissing is enabled, object members whose values are null are
 * omitted before SchemaPlan fallback. Array null elements are preserved.
 * The ordinary validator path never allocates this projection.</p>
 */
public final class SchemaValidationInput {
    private SchemaValidationInput() { }

    /**
     * Copies object/array containers recursively, omitting null object members.
     * Scalars and custom value-codec instances retain their original types.
     */
    public static Object omitNullMembers(Object value) {
        if (value == null) return null;
        JsonType kind = JsonType.of(value);
        if (kind == JsonType.OBJECT) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : Nodes.entrySetInObject(value)) {
                if (entry.getValue() != null) {
                    result.put(entry.getKey(), omitNullMembers(entry.getValue()));
                }
            }
            return result;
        }
        if (kind == JsonType.ARRAY) {
            List<Object> result = new ArrayList<>();
            java.util.Iterator<Object> iterator = Nodes.iteratorInArray(value);
            while (iterator.hasNext()) result.add(omitNullMembers(iterator.next()));
            return result;
        }
        return value;
    }
}
