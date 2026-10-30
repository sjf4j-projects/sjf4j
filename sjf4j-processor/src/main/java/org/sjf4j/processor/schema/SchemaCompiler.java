package org.sjf4j.processor.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.NodeKind;
import org.sjf4j.Nodes;
import org.sjf4j.Sjf4j;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.property.Property;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Analyzes schema constraints and Java types without emitting Java source.
 * Unsupported properties or array items are represented as local fallbacks;
 * unsafe root scopes are left to the complete runtime plan.
 */
final class SchemaCompiler {
    private final ProcessorContext context;

    SchemaCompiler(ProcessorContext context) {
        this.context = context;
    }

    SchemaCheck.ObjectCheck compileObject(TypeMirror type,
                                           SchemaResourceResolver.Snapshot snapshot,
                                           boolean nullAsMissing) {
        NodeKind nodeKind = context.types.nodeKind(type);
        boolean dynamic = nodeKind == NodeKind.OBJECT_MAP ||
                nodeKind == NodeKind.OBJECT_JSON_OBJECT || nodeKind == NodeKind.OBJECT_JOJO;
        if (!dynamic && nodeKind != NodeKind.OBJECT_POJO) return null;
        TypeElement owner = context.types.typeElement(type);
        if (!dynamic && (owner == null || !owner.getModifiers().contains(Modifier.FINAL) ||
                !owner.getModifiers().contains(Modifier.PUBLIC) ||
                owner.getAnnotation(org.sjf4j.annotation.node.NodeObject.class) != null)) return null;

        Object parsed = Sjf4j.global().fromJson(snapshot.documents[0]);
        if (JsonType.of(parsed) != JsonType.OBJECT) return null;
        JsonObject root = Nodes.toJsonObject(parsed);
        String rootUri = snapshot.uris[0];
        if (!snapshot.selectedUri.equals(snapshot.uris[0])) {
            RefTarget selected = resolveRef(snapshot,
                    JsonObject.of("$ref", snapshot.selectedUri), snapshot.uris[0]);
            if (selected == null || JsonType.of(selected.value) != JsonType.OBJECT) return null;
            root = Nodes.toJsonObject(selected.value);
            rootUri = selected.uri;
        }
        if (root.containsKey("$ref")) {
            RefTarget target = resolveRef(snapshot, root, snapshot.selectedUri);
            if (target == null || JsonType.of(target.value) != JsonType.OBJECT) return null;
            root = Nodes.toJsonObject(target.value);
            rootUri = target.uri;
        }
        for (String key : root.keySet()) {
            if (!key.equals("type") && !key.equals("properties") && !key.equals("required")
                    && !key.equals("additionalProperties") && !isAnnotation(key)) return null;
        }
        if (!"object".equals(root.getString("type"))) return null;
        Object raw = root.getNode("properties");
        if (JsonType.of(raw) != JsonType.OBJECT) return null;
        JsonObject propertyMap = Nodes.toJsonObject(raw);
        Map<String, Property> javaProperties = context.properties.resolve(type);
        if (!dynamic && javaProperties.isEmpty()) return null;

        Object required = root.getNode("required");
        if (required != null) {
            if (JsonType.of(required) != JsonType.ARRAY) return null;
            for (int i = 0; i < Nodes.sizeInArray(required); i++) {
                Object name = Nodes.getInArray(required, i);
                if (!(name instanceof String) || (!dynamic && !javaProperties.containsKey(name))) return null;
            }
        }
        Object additional = root.getNode("additionalProperties");
        if (additional != null) {
            if (!(additional instanceof Boolean)) return null;
            if (!dynamic && Boolean.FALSE.equals(additional)
                    && !propertyMap.keySet().containsAll(javaProperties.keySet())) return null;
        }
        for (String name : propertyMap.keySet()) {
            if (!dynamic && !javaProperties.containsKey(name) && isRequired(required, name)) return null;
        }
        if (!dynamic) {
            for (Property property : javaProperties.values()) {
                if (property.read() == null ||
                        !property.read().member().getModifiers().contains(Modifier.PUBLIC) ||
                        (property.read().isMethod() &&
                                !((ExecutableElement) property.read().member()).getThrownTypes().isEmpty()) ||
                        !property.name().matches("[a-zA-Z0-9_]+")) return null;
            }
        }

        List<SchemaCheck.Required> requiredChecks = new ArrayList<SchemaCheck.Required>();
        if (required != null) {
            for (int i = 0; i < Nodes.sizeInArray(required); i++) {
                String name = (String) Nodes.getInArray(required, i);
                Property property = dynamic ? null : javaProperties.get(name);
                requiredChecks.add(new SchemaCheck.Required(name, property == null ? null
                        : property.read().memberName() + (property.read().isMethod() ? "()" : "")));
            }
        }

        List<SchemaCheck.PropertyCheck> properties = new ArrayList<SchemaCheck.PropertyCheck>();
        int directCount = (required != null ? 1 : 0) + (dynamic && Boolean.FALSE.equals(additional) ? 1 : 0);
        for (String name : propertyMap.keySet()) {
            Property property = dynamic ? null : javaProperties.get(name);
            if (!dynamic && property == null) continue;
            TypeMirror declared = property == null ? null : property.read().type();
            String access = property == null ? null
                    : property.read().memberName() + (property.read().isMethod() ? "()" : "");
            String uri = childPointer(rootUri, "properties", name);
            SchemaValue value = SchemaValue.of(context, declared);
            SchemaCheck check = compileValue(snapshot, propertyMap.getNode(name), value, uri, 0);
            if (check == null) check = new SchemaCheck.Fallback(uri);
            else directCount++;
            properties.add(new SchemaCheck.PropertyCheck(name, access, value, check));
        }
        if (directCount == 0) return null;
        return new SchemaCheck.ObjectCheck(dynamic, nullAsMissing,
                Boolean.FALSE.equals(additional), new ArrayList<String>(propertyMap.keySet()),
                requiredChecks, properties);
    }

    private SchemaCheck compileValue(SchemaResourceResolver.Snapshot snapshot,
                                     Object rawSchema, SchemaValue value,
                                     String schemaUri, int depth) {
        if (depth > 8) return null;
        if (rawSchema instanceof Boolean) {
            return Boolean.TRUE.equals(rawSchema) ? new SchemaCheck.Pass() : null;
        }
        if (JsonType.of(rawSchema) != JsonType.OBJECT) return null;
        JsonObject schema = Nodes.toJsonObject(rawSchema);
        if (schema.containsKey("$ref")) {
            RefTarget target = resolveRef(snapshot, schema, schemaUri);
            if (target == null) return null;
            return compileValue(snapshot, target.value, value, target.uri, depth + 1);
        }
        String kind = schema.getString("type");
        if (kind == null) return null;

        if ("string".equals(kind)) {
            if (value.shape != SchemaValue.Shape.STRING && value.shape != SchemaValue.Shape.DYNAMIC) return null;
            for (String key : schema.keySet()) {
                if (!key.equals("type") && !key.equals("minLength") && !key.equals("maxLength")
                        && !key.equals("enum") && !isAnnotation(key)) return null;
            }
            Integer min = schema.getInt("minLength");
            Integer max = schema.getInt("maxLength");
            if ((schema.containsKey("minLength") && (min == null || min < 0))
                    || (schema.containsKey("maxLength") && (max == null || max < 0))) return null;
            Object enumeration = schema.getNode("enum");
            List<String> choices = null;
            if (enumeration != null) {
                if (JsonType.of(enumeration) != JsonType.ARRAY) return null;
                choices = new ArrayList<String>();
                for (int i = 0; i < Nodes.sizeInArray(enumeration); i++) {
                    Object item = Nodes.getInArray(enumeration, i);
                    if (!(item instanceof String)) return null;
                    choices.add((String) item);
                }
            }
            return new SchemaCheck.StringCheck(min, max, choices);
        }

        if ("number".equals(kind) || "integer".equals(kind)) {
            if (value.shape != SchemaValue.Shape.NUMBER && value.shape != SchemaValue.Shape.DYNAMIC) return null;
            for (String key : schema.keySet()) {
                if (!key.equals("type") && !key.equals("minimum") && !key.equals("maximum")
                        && !key.equals("exclusiveMinimum") && !key.equals("exclusiveMaximum")
                        && !isAnnotation(key)) return null;
            }
            List<SchemaCheck.NumericBound> bounds = new ArrayList<SchemaCheck.NumericBound>();
            for (String key : new String[]{"minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum"}) {
                Object limit = schema.getNode(key);
                if (limit == null) continue;
                if (!(limit instanceof Number)) return null;
                bounds.add(new SchemaCheck.NumericBound(key, (Number) limit));
            }
            return new SchemaCheck.NumberCheck("integer".equals(kind), bounds);
        }

        if ("array".equals(kind)) {
            if (!value.isArrayLike() && value.shape != SchemaValue.Shape.DYNAMIC) return null;
            for (String key : schema.keySet()) {
                if (!key.equals("type") && !key.equals("items") && !key.equals("minItems")
                        && !key.equals("maxItems") && !isAnnotation(key)) return null;
            }
            Integer min = schema.getInt("minItems");
            Integer max = schema.getInt("maxItems");
            if ((schema.containsKey("minItems") && (min == null || min < 0))
                    || (schema.containsKey("maxItems") && (max == null || max < 0))) return null;
            SchemaCheck itemCheck = null;
            if (schema.containsKey("items")) {
                String itemUri = schemaUri + "/items";
                SchemaValue itemValue = SchemaValue.of(context, value.itemType());
                itemCheck = compileValue(snapshot, schema.getNode("items"), itemValue, itemUri, depth + 1);
                if (itemCheck == null) itemCheck = new SchemaCheck.Fallback(itemUri);
            }
            return new SchemaCheck.ArrayCheck(min, max, itemCheck,
                    itemCheck == null ? null : SchemaValue.of(context, value.itemType()));
        }
        return null;
    }

    private static String pointerEscape(String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }

    private static String childPointer(String base, String keyword, String name) {
        return base + (base.contains("#") ? "/" : "#/")
                + pointerEscape(keyword) + "/" + pointerEscape(name);
    }

    /** Only JSON Schema annotation / definition keywords may be skipped. */
    private static boolean isAnnotation(String key) {
        return key.equals("$schema") || key.equals("$id") || key.equals("title")
                || key.equals("description") || key.equals("default") || key.equals("examples")
                || key.equals("$comment") || key.equals("deprecated") || key.equals("readOnly")
                || key.equals("writeOnly") || key.equals("definitions") || key.equals("$defs");
    }

    private static boolean isRequired(Object required, String key) {
        if (required == null) return false;
        for (int i = 0; i < org.sjf4j.Nodes.sizeInArray(required); i++) {
            Object value = org.sjf4j.Nodes.getInArray(required, i);
            if (key.equals(value)) return true;
        }
        return false;
    }


    /** A compile-time resolution only; unsupported anchors or sibling keywords fall back. */
    private static RefTarget resolveRef(SchemaResourceResolver.Snapshot snapshot,
                                        org.sjf4j.JsonObject schema, String sourceUri) {
        for (String keyword : schema.keySet()) {
            if (!keyword.equals("$ref") && !isAnnotation(keyword)) return null;
        }
        String literal = schema.getString("$ref");
        if (literal == null) return null;
        java.net.URI target;
        try {
            target = java.net.URI.create(sourceUri).resolve(literal);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String document = org.sjf4j.schema.SchemaLoader.documentUri(target).toString();
        Object node = null;
        for (int i = 0; i < snapshot.uris.length; i++) {
            if (snapshot.uris[i].equals(document)) {
                node = org.sjf4j.Sjf4j.global().fromJson(snapshot.documents[i]);
                break;
            }
        }
        if (node == null) return null;
        String fragment = target.getFragment();
        if (fragment != null && !fragment.isEmpty()) {
            if (!fragment.startsWith("/")) return null; // Named anchors need planner resolution.
            String[] segments = fragment.substring(1).split("/", -1);
            for (String part : segments) {
                String name = part.replace("~1", "/").replace("~0", "~");
                if (org.sjf4j.JsonType.of(node) == org.sjf4j.JsonType.OBJECT) {
                    node = org.sjf4j.Nodes.getInObject(node, name);
                } else if (org.sjf4j.JsonType.of(node) == org.sjf4j.JsonType.ARRAY) {
                    try {
                        node = org.sjf4j.Nodes.getInArray(node, Integer.parseInt(name));
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                } else return null;
                if (node == null) return null;
            }
        }
        return new RefTarget(node, target.toString());
    }

    private static final class RefTarget {
        final Object value;
        final String uri;
        RefTarget(Object value, String uri) {
            this.value = value;
            this.uri = uri;
        }
    }


}
