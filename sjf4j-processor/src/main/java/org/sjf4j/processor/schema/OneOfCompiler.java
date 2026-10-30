package org.sjf4j.processor.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.OneOfMapping;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.property.Property;
import org.sjf4j.processor.property.PropertyAccess;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Directly validates disjoint OneOf subtypes only when the JSON Schema
 * discriminator and the Java object's public constant property prove that
 * precisely one branch can match. This is a schema assertion optimization,
 * not a shortcut through binding's discriminator alone.
 */
final class OneOfCompiler {
    private final ProcessorContext context;
    private final ObjectCompiler objects;

    OneOfCompiler(ProcessorContext context, ObjectCompiler objects) {
        this.context = context;
        this.objects = objects;
    }

    SchemaCheck compile(SchemaResourceResolver.Snapshot snapshot, JsonObject schema,
                        SchemaValue value, String uri, int depth) {
        if (value.type == null || depth >= 8) return null;
        for (String key : schema.keySet()) {
            if (!key.equals("oneOf") && !SchemaCompiler.isAnnotation(key)) return null;
        }
        TypeElement parent = context.types.typeElement(value.type);
        if (parent == null) return null;
        OneOf mapping = parent.getAnnotation(OneOf.class);
        if (mapping == null || mapping.scope() != OneOf.Scope.CURRENT
                || mapping.discriminatorKey().isEmpty() || !mapping.discriminatorPath().isEmpty()) return null;

        Object candidates = schema.getNode("oneOf");
        if (JsonType.of(candidates) != JsonType.ARRAY) return null;
        int count = Nodes.sizeInArray(candidates);
        if (count < 2 || count != mapping.mappings().length) return null;

        String discriminator = mapping.discriminatorKey();
        List<SchemaCheck.OneOfCheck.Subtype> compiled = new ArrayList<>();
        Set<String> values = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String childUri = uri + "/oneOf/" + i;
            Object raw = Nodes.getInArray(candidates, i);
            if (JsonType.of(raw) != JsonType.OBJECT) return null;
            JsonObject ref = Nodes.toJsonObject(raw);
            SchemaCompiler.RefTarget target = SchemaCompiler.resolveRef(snapshot, ref, childUri);
            if (target == null || JsonType.of(target.value) != JsonType.OBJECT) return null;
            JsonObject branch = Nodes.toJsonObject(target.value);

            String expected = requiredDiscriminator(branch, discriminator);
            if (expected == null || !values.add(expected)) return null;

            OneOfMapping candidate = null;
            for (OneOfMapping declared : mapping.mappings()) {
                if (declared.when().length == 1 && expected.equals(declared.when()[0])) {
                    if (candidate != null) return null;
                    candidate = declared;
                }
            }
            if (candidate == null) return null;

            TypeMirror subtype = mappedType(candidate);
            if (subtype == null || !expected.equals(publicConstant(subtype, discriminator))) return null;
            SchemaCheck.ObjectCheck check =
                    objects.compileNested(subtype, snapshot, branch, target.uri, false);
            // No per-property fallback is allowed on the direct path. The
            // original oneOf subplan remains the single fallback for strict
            // format assertions and unknown runtime classes.
            if (check == null || check.hasFallback()) return null;
            compiled.add(new SchemaCheck.OneOfCheck.Subtype(subtype.toString(), check));
        }
        return new SchemaCheck.OneOfCheck(uri, compiled);
    }

    private TypeMirror mappedType(OneOfMapping mapping) {
        try {
            Class<?> clazz = mapping.type();
            TypeElement type = context.elements.getTypeElement(clazz.getCanonicalName());
            return type == null ? null : type.asType();
        } catch (MirroredTypeException ex) {
            return ex.getTypeMirror();
        }
    }

    private String publicConstant(TypeMirror subtype, String name) {
        TypeElement owner = context.types.typeElement(subtype);
        if (owner == null || !owner.getModifiers().contains(Modifier.FINAL)) return null;
        for (javax.lang.model.element.Element enclosing = owner;
             enclosing instanceof TypeElement; enclosing = enclosing.getEnclosingElement()) {
            if (!enclosing.getModifiers().contains(Modifier.PUBLIC)) return null;
        }
        Property property = context.properties.resolve(subtype).get(name);
        if (property == null || property.read() == null) return null;
        PropertyAccess access = property.read();
        if (!(access.member() instanceof VariableElement)
                || !access.member().getModifiers().contains(Modifier.PUBLIC)
                || !access.member().getModifiers().contains(Modifier.FINAL)
                || access.member().getModifiers().contains(Modifier.STATIC)) return null;
        Object constant = ((VariableElement) access.member()).getConstantValue();
        return constant instanceof String ? (String) constant : null;
    }

    private static String requiredDiscriminator(JsonObject branch, String name) {
        if (!"object".equals(branch.getString("type"))) return null;
        Object required = branch.getNode("required");
        if (JsonType.of(required) != JsonType.ARRAY) return null;
        boolean present = false;
        for (int i = 0; i < Nodes.sizeInArray(required); i++) {
            if (name.equals(Nodes.getInArray(required, i))) {
                present = true;
                break;
            }
        }
        if (!present) return null;
        Object properties = branch.getNode("properties");
        if (JsonType.of(properties) != JsonType.OBJECT) return null;
        Object field = Nodes.getInObject(properties, name);
        if (JsonType.of(field) != JsonType.OBJECT) return null;
        JsonObject shape = Nodes.toJsonObject(field);
        if (!"string".equals(shape.getString("type"))) return null;
        Object values = shape.getNode("enum");
        if (JsonType.of(values) != JsonType.ARRAY || Nodes.sizeInArray(values) != 1) return null;
        Object unique = Nodes.getInArray(values, 0);
        return unique instanceof String ? (String) unique : null;
    }
}
