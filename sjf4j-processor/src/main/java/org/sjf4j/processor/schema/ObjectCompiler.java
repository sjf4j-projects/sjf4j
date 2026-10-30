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
 * Compiles direct object checks for statically readable POJOs and dynamic
 * object nodes, retaining full-scope fallback for unsupported root keywords.
 */
final class ObjectCompiler {
    private final ProcessorContext context;
    private final SchemaCompiler schemaCompiler;

    ObjectCompiler(ProcessorContext context, SchemaCompiler schemaCompiler) {
        this.context = context;
        this.schemaCompiler = schemaCompiler;
    }

    SchemaCheck.ObjectCheck compile(TypeMirror type,
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
            SchemaCompiler.RefTarget selected = SchemaCompiler.resolveRef(snapshot,
                    JsonObject.of("$ref", snapshot.selectedUri), snapshot.uris[0]);
            if (selected == null || JsonType.of(selected.value) != JsonType.OBJECT) return null;
            root = Nodes.toJsonObject(selected.value);
            rootUri = selected.uri;
        }
        if (root.containsKey("$ref")) {
            SchemaCompiler.RefTarget target = SchemaCompiler.resolveRef(snapshot, root, snapshot.selectedUri);
            if (target == null || JsonType.of(target.value) != JsonType.OBJECT) return null;
            root = Nodes.toJsonObject(target.value);
            rootUri = target.uri;
        }
        return compileResolved(type, snapshot, root, rootUri, nullAsMissing, 0);
    }

    /** Compiles a concrete nested object without changing root schema selection. */
    SchemaCheck.ObjectCheck compileNested(TypeMirror type, SchemaResourceResolver.Snapshot snapshot,
                                           JsonObject schema, String schemaUri, boolean nullAsMissing,
                                           int depth) {
        NodeKind kind = context.types.nodeKind(type);
        boolean dynamic = kind == NodeKind.OBJECT_MAP ||
                kind == NodeKind.OBJECT_JSON_OBJECT || kind == NodeKind.OBJECT_JOJO;
        if (!dynamic && kind != NodeKind.OBJECT_POJO) return null;
        if (!dynamic) {
            TypeElement owner = context.types.typeElement(type);
            if (owner == null || !owner.getModifiers().contains(Modifier.PUBLIC)
                    || !owner.getModifiers().contains(Modifier.FINAL)
                    || owner.getAnnotation(org.sjf4j.annotation.node.NodeObject.class) != null) return null;
        }
        return compileResolved(type, snapshot, schema, schemaUri, nullAsMissing, depth);
    }

    private SchemaCheck.ObjectCheck compileResolved(TypeMirror type,
                    SchemaResourceResolver.Snapshot snapshot, JsonObject root,
                    String rootUri, boolean nullAsMissing, int depth) {
        NodeKind nodeKind = context.types.nodeKind(type);
        boolean dynamic = nodeKind == NodeKind.OBJECT_MAP ||
                nodeKind == NodeKind.OBJECT_JSON_OBJECT || nodeKind == NodeKind.OBJECT_JOJO;
        // The direct POJO path only understands the public structural projection.
        // Non-public annotated members may be visible to runtime reflection and
        // must not silently disappear from additionalProperties/required checks.
        if (!dynamic && hasHiddenNodeMembers(type)) return null;
        for (String key : root.keySet()) {
            if (!key.equals("type") && !key.equals("properties") && !key.equals("required")
                    && !key.equals("additionalProperties") && !SchemaCompiler.isAnnotation(key)) return null;
        }
        if (!"object".equals(root.getString("type"))) return null;
        Object raw = root.getNode("properties");
        if (JsonType.of(raw) != JsonType.OBJECT) return null;
        JsonObject propertyMap = Nodes.toJsonObject(raw);
        Map<String, Property> javaProperties = context.properties.resolve(type);
        // An object with no declared properties still enforces its JSON type.

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
                                !((ExecutableElement) property.read().member()).getThrownTypes().isEmpty())) return null;
            }
        }

        List<SchemaCheck.Required> requiredChecks = new ArrayList<SchemaCheck.Required>();
        if (required != null) {
            for (int i = 0; i < Nodes.sizeInArray(required); i++) {
                String name = (String) Nodes.getInArray(required, i);
                Property property = dynamic ? null : javaProperties.get(name);
                if (!dynamic && (property == null || property.read() == null)) return null;
                // Primitive members cannot be null, so a required check is redundant.
                String requiredAccess = property == null || property.read().type().getKind().isPrimitive()
                        ? null : property.read().memberName() + (property.read().isMethod() ? "()" : "");
                requiredChecks.add(new SchemaCheck.Required(name, requiredAccess));
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
            SchemaCheck check = schemaCompiler.compileValue(snapshot, propertyMap.getNode(name), value, uri, depth + 1);
            if (check == null) check = new SchemaCheck.Fallback(uri);
            else directCount++;
            properties.add(new SchemaCheck.PropertyCheck(name, access, value, check));
        }
        if (directCount == 0) return null;
        return new SchemaCheck.ObjectCheck(dynamic, nullAsMissing,
                Boolean.FALSE.equals(additional), new ArrayList<String>(propertyMap.keySet()),
                requiredChecks, properties);
    }

    private boolean hasHiddenNodeMembers(TypeMirror type) {
        TypeElement owner = context.types.typeElement(type);
        for (TypeElement current = owner; current != null; ) {
            for (javax.lang.model.element.Element member : current.getEnclosedElements()) {
                if (member.getModifiers().contains(Modifier.STATIC)) continue;
                if (member.getAnnotation(org.sjf4j.annotation.node.NodeProperty.class) != null
                        && !member.getModifiers().contains(Modifier.PUBLIC)) return true;
                if (member.getAnnotation(org.sjf4j.annotation.node.NodeIgnore.class) != null) return true;
            }
            TypeMirror parent = current.getSuperclass();
            current = parent.getKind() == javax.lang.model.type.TypeKind.NONE
                    ? null : context.types.typeElement(parent);
        }
        return false;
    }

    private static String pointerEscape(String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }

    private static String childPointer(String base, String keyword, String name) {
        return base + (base.contains("#") ? "/" : "#/")
                + pointerEscape(keyword) + "/" + pointerEscape(name);
    }

    private static boolean isRequired(Object required, String key) {
        if (required == null) return false;
        for (int i = 0; i < org.sjf4j.Nodes.sizeInArray(required); i++) {
            Object value = org.sjf4j.Nodes.getInArray(required, i);
            if (key.equals(value)) return true;
        }
        return false;
    }
}
