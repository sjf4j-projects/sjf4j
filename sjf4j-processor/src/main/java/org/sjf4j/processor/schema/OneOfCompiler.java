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
 * Compiles each schema branch against the declared Java type first.
 * Known concrete @OneOf subtypes get their own complete oneOf branch set;
 * provably incompatible constant discriminators prune individual branches.
 * Unknown runtime classes retain the generic compiled oneOf branch counting.
 */
final class OneOfCompiler {
    private final ProcessorContext context;
    private final ObjectCompiler objects;
    private final SchemaCompiler compiler;

    OneOfCompiler(ProcessorContext context, ObjectCompiler objects, SchemaCompiler compiler) {
        this.context = context;
        this.objects = objects;
        this.compiler = compiler;
    }

    SchemaCheck compile(SchemaResourceResolver.Snapshot snapshot, JsonObject schema,
                        SchemaValue value, String uri, int depth) {
        for (String key : schema.keySet()) {
            if (!key.equals("oneOf") && !SchemaCompiler.isAnnotation(key)) return null;
        }
        SchemaCheck generic = compileGeneric(snapshot, schema, value, uri, depth);
        SchemaCheck specialized = generic instanceof SchemaCheck.GenericOneOfCheck
                ? compileSpecialized(snapshot, schema, value, uri, depth,
                        (SchemaCheck.GenericOneOfCheck) generic)
                : null;
        return specialized != null ? specialized : generic;
    }

    private SchemaCheck compileGeneric(SchemaResourceResolver.Snapshot snapshot, JsonObject schema,
                                       SchemaValue value, String uri, int depth) {
        Object candidates = schema.getNode("oneOf");
        if (JsonType.of(candidates) != JsonType.ARRAY) return null;
        int count = Nodes.sizeInArray(candidates);
        if (count < 1) return null;
        List<SchemaCheck> branches = new ArrayList<SchemaCheck>(count);
        for (int i = 0; i < count; i++) {
            String childUri = SchemaCompiler.childUri(uri, "oneOf", i);
            SchemaCheck branch = compiler.compileValue(snapshot,
                    Nodes.getInArray(candidates, i), value, childUri, depth + 1);
            // Do not compose a boolean branch with a stateful fallback. A
            // full oneOf SchemaPlan handles unsupported branches and evaluated tracking.
            if (branch == null || branch.hasFallback()) return null;
            branches.add(branch);
        }
        return new SchemaCheck.GenericOneOfCheck(
                SchemaCompiler.diagnostic(uri, "oneOf"), branches);
    }

    private SchemaCheck compileSpecialized(SchemaResourceResolver.Snapshot snapshot, JsonObject schema,
                        SchemaValue value, String uri, int depth,
                        SchemaCheck.GenericOneOfCheck generic) {
        if (value.type == null || generic == null) return null;
        TypeElement parent = context.types.typeElement(value.type);
        if (parent == null) return null;
        OneOf mapping = parent.getAnnotation(OneOf.class);
        if (mapping == null || mapping.scope() != OneOf.Scope.CURRENT
                || mapping.discriminatorKey().isEmpty() || !mapping.discriminatorPath().isEmpty()) return null;

        Object candidates = schema.getNode("oneOf");
        if (JsonType.of(candidates) != JsonType.ARRAY) return null;
        int count = Nodes.sizeInArray(candidates);
        if (count < 2 || mapping.mappings().length == 0) return null;

        String discriminator = mapping.discriminatorKey();
        // The schema itself (not @OneOfMapping) must prove that exactly one
        // branch can match any given discriminator. Any unknown/overlapping
        // branch disables guarded dispatch, not generic oneOf compilation.
        String[] schemaValues = new String[count];
        Set<String> distinctValues = new HashSet<>();
        boolean disjoint = true;
        for (int i = 0; i < count; i++) {
            String expected = schemaDiscriminator(snapshot,
                    Nodes.getInArray(candidates, i),
                    SchemaCompiler.childUri(uri, "oneOf", i), discriminator);
            if (expected == null || !distinctValues.add(expected)) {
                disjoint = false;
                break;
            }
            schemaValues[i] = expected;
        }
        List<SchemaCheck.OneOfCheck.Subtype> compiled = new ArrayList<>();
        Set<String> seenTypes = new HashSet<>();
        for (OneOfMapping candidate : mapping.mappings()) {
            TypeMirror subtype = mappedType(candidate);
            if (subtype == null || !seenTypes.add(subtype.toString())) return null;
            TypeElement owner = context.types.typeElement(subtype);
            // Generated code has to reference and cast the exact concrete type.
            if (owner == null || !owner.getModifiers().contains(Modifier.PUBLIC)
                    || !owner.getModifiers().contains(Modifier.FINAL)) return null;
            for (javax.lang.model.element.Element enclosing = owner.getEnclosingElement();
                    enclosing instanceof TypeElement; enclosing = enclosing.getEnclosingElement()) {
                if (!enclosing.getModifiers().contains(Modifier.PUBLIC)) return null;
            }

            SchemaValue subtypeValue = SchemaValue.of(context, subtype);
            String constant = publicConstant(subtype, discriminator);
            List<SchemaCheck> branches = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String childUri = SchemaCompiler.childUri(uri, "oneOf", i);
                Object raw = Nodes.getInArray(candidates, i);
                // Only prune when we can prove the JSON-visible discriminator
                // is a constant and the branch requires a different value.
                String expected = constant == null ? null
                        : schemaDiscriminator(snapshot, raw, childUri, discriminator);
                if (expected != null && !expected.equals(constant)) {
                    branches.add(new SchemaCheck.Reject(SchemaCompiler.diagnostic(childUri, "oneOf")));
                    continue;
                }
                SchemaCheck check = compiler.compileValue(snapshot, raw, subtypeValue, childUri, depth + 1);
                // Never silently drop an uncompiled branch: that would change
                // oneOf's exactly-one-match semantics.
                if (check == null || check.hasFallback()) return null;
                branches.add(check);
            }
            String guardAccess = null;
            String guardValue = null;
            int guardIndex = -1;
            if (disjoint && candidate.when().length == 1) {
                String expected = candidate.when()[0];
                for (int i = 0; i < count; i++) {
                    if (!expected.equals(schemaValues[i])) continue;
                    Property property = context.properties.resolve(subtype).get(discriminator);
                    if (property != null && property.read() != null && !property.explicitCodec()) {
                        PropertyAccess access = property.read();
                        javax.lang.model.element.Element member = access.member();
                        if (member.getModifiers().contains(Modifier.PUBLIC)
                                && !member.getModifiers().contains(Modifier.STATIC)
                                && member.getAnnotation(org.sjf4j.annotation.node.NodeIgnore.class) == null
                                && "java.lang.String".equals(access.type().toString())) {
                            guardAccess = access.memberName() + (access.isMethod() ? "()" : "");
                            guardValue = expected;
                            guardIndex = i;
                        }
                    }
                    break;
                }
            }
            compiled.add(new SchemaCheck.OneOfCheck.Subtype(subtype.toString(),
                    subtypeValue, new SchemaCheck.GenericOneOfCheck(
                    SchemaCompiler.diagnostic(uri, "oneOf"), branches),
                    guardAccess, guardValue, guardIndex));
        }
        return new SchemaCheck.OneOfCheck(uri,
                SchemaCompiler.diagnostic(uri, "oneOf"), compiled, generic);
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
        if (property == null || property.read() == null || property.explicitCodec()) return null;
        PropertyAccess access = property.read();
        if (!(access.member() instanceof VariableElement)
                || !access.member().getModifiers().contains(Modifier.PUBLIC)
                || !access.member().getModifiers().contains(Modifier.FINAL)
                || access.member().getModifiers().contains(Modifier.STATIC)) return null;
        Object constant = ((VariableElement) access.member()).getConstantValue();
        return constant instanceof String ? (String) constant : null;
    }

    private static String schemaDiscriminator(SchemaResourceResolver.Snapshot snapshot,
                    Object raw, String uri, String discriminator) {
        if (JsonType.of(raw) != JsonType.OBJECT) return null;
        JsonObject object = Nodes.toJsonObject(raw);
        if (object.containsKey("$ref")) {
            SchemaCompiler.RefTarget target = SchemaCompiler.resolveRef(snapshot, object, uri);
            if (target == null || JsonType.of(target.value) != JsonType.OBJECT) return null;
            object = SchemaCompiler.schemaObject(target.value);
        }
        return requiredDiscriminator(object, discriminator);
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
