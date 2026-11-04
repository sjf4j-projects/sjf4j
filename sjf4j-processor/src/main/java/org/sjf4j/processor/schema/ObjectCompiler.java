package org.sjf4j.processor.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.NodeKind;
import org.sjf4j.Nodes;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.property.Property;
import org.sjf4j.processor.discovery.CompiledMetadata;

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

    /** Compiles a concrete nested object without changing root schema selection. */
    SchemaCheck compileNested(TypeMirror type, SchemaResourceResolver.Snapshot snapshot,
                                           JsonObject schema, String schemaUri,
                                           int depth) {
        if (!useDynamicAccess(type) && context.types.nodeKind(type) != NodeKind.OBJECT_POJO)
            return null;
        return compileResolved(type, snapshot, schema, schemaUri, depth);
    }

    private SchemaCheck compileResolved(TypeMirror type,
                    SchemaResourceResolver.Snapshot snapshot, JsonObject root,
                    String rootUri, int depth) {
        boolean dynamic = useDynamicAccess(type);
        // The direct POJO path only understands the public structural projection.
        // Non-public annotated members may be visible to runtime reflection and
        // must not silently disappear from additionalProperties/required checks.
        if (!dynamic && hasHiddenNodeMembers(type)) return null;
        // Property-name enumeration and evaluated-name coverage are dynamic
        // traversal decisions, not runtime SchemaPlan fallbacks.
        if (!dynamic && (root.containsKey("patternProperties")
                || root.containsKey("propertyNames") || root.containsKey("dependentSchemas")
                || (root.containsKey("additionalProperties")
                    && !(root.getNode("additionalProperties") instanceof Boolean)))) return null;
        for (String key : root.keySet()) {
            if (!key.equals("type") && !key.equals("properties") && !key.equals("patternProperties")
                    && !key.equals("propertyNames") && !key.equals("required")
                    && !key.equals("additionalProperties") && !key.equals("minProperties")
                    && !key.equals("maxProperties") && !key.equals("dependentRequired")
                    && !key.equals("dependentSchemas")
                    && !SchemaCompiler.isAnnotation(key)) return null;
        }
        boolean typeRequired = root.containsKey("type");
        if (typeRequired && !"object".equals(root.getNode("type"))) return null;
        if (!typeRequired && !root.containsKey("properties") && !root.containsKey("patternProperties")
                && !root.containsKey("propertyNames") && !root.containsKey("required")
                && !root.containsKey("additionalProperties") && !root.containsKey("minProperties")
                && !root.containsKey("maxProperties") && !root.containsKey("dependentRequired")
                && !root.containsKey("dependentSchemas")) return null;
        Object raw = root.getNode("properties");
        if (raw != null && JsonType.of(raw) != JsonType.OBJECT) return null;
        JsonObject propertyMap = raw == null ? new JsonObject() : Nodes.toJsonObject(raw);
        Object rawPatterns = root.getNode("patternProperties");
        if (root.containsKey("patternProperties") && JsonType.of(rawPatterns) != JsonType.OBJECT) return null;
        JsonObject patternMap = rawPatterns == null ? new JsonObject() : Nodes.toJsonObject(rawPatterns);
        List<SchemaCheck.PatternProperty> patternChecks = new ArrayList<SchemaCheck.PatternProperty>();
        for (String expression : patternMap.keySet()) {
            String javaPattern;
            try {
                javaPattern = org.sjf4j.schema.SchemaUtil.compileRegexPattern(
                        expression, "patternProperties", org.sjf4j.path.PathSegment.Root.INSTANCE,
                        java.net.URI.create("sjf4j:/compiled")).pattern();
            } catch (RuntimeException ex) {
                return null;
            }
            SchemaCheck patternCheck = schemaCompiler.compileValue(snapshot, patternMap.getNode(expression),
                    SchemaValue.of(context, null), childPointer(rootUri, "patternProperties", expression), depth + 1);
            if (patternCheck == null || patternCheck.hasFallback()) return null;
            patternChecks.add(new SchemaCheck.PatternProperty(javaPattern, patternCheck));
        }
        // propertyNames validates each JSON member *name* as a string, not
        // the value. The compiled child is independent of the instance's Java
        // representation and of the separate type assertion.
        SchemaCheck propertyNamesCheck = null;
        if (root.containsKey("propertyNames")) {
            propertyNamesCheck = schemaCompiler.compileValue(snapshot, root.getNode("propertyNames"),
                    SchemaValue.of(context, null),
                    SchemaCompiler.keywordUri(rootUri, "propertyNames"), depth + 1);
            if (propertyNamesCheck == null || propertyNamesCheck.hasFallback()) return null;
        }
        // dependentSchemas applies its child schema to the entire object when
        // the trigger member is present, including when its value is null.
        // Its child does not contribute to additionalProperties coverage.
        List<SchemaCheck.DependentSchema> dependentSchemaChecks =
                new ArrayList<SchemaCheck.DependentSchema>();
        if (root.containsKey("dependentSchemas")) {
            Object rawSchemaMap = root.getNode("dependentSchemas");
            if (JsonType.of(rawSchemaMap) != JsonType.OBJECT) return null;
            JsonObject schemaMap = Nodes.toJsonObject(rawSchemaMap);
            for (String trigger : schemaMap.keySet()) {
                SchemaCheck child = schemaCompiler.compileValue(snapshot, schemaMap.getNode(trigger),
                        SchemaValue.of(context, null),
                        childPointer(rootUri, "dependentSchemas", trigger), depth + 1);
                if (child == null || child.hasFallback()) return null;
                if (child.kind != SchemaCheck.Kind.PASS) {
                    dependentSchemaChecks.add(new SchemaCheck.DependentSchema(trigger, child));
                }
            }
        }
        Map<String, Property> javaProperties = dynamic ? java.util.Collections.emptyMap()
                : context.properties.resolve(type);

        Integer minProperties = root.getInt("minProperties");
        Integer maxProperties = root.getInt("maxProperties");
        if ((root.containsKey("minProperties") && (minProperties == null || minProperties < 0))
                || (root.containsKey("maxProperties") && (maxProperties == null || maxProperties < 0)))
            return null;
        Object required = root.getNode("required");
        if (required != null) {
            if (JsonType.of(required) != JsonType.ARRAY) return null;
            for (int i = 0; i < Nodes.sizeInArray(required); i++) {
                Object name = Nodes.getInArray(required, i);
                if (!(name instanceof String)) return null;
                if (!dynamic && !javaProperties.containsKey(name)) {
                    // The complete final POJO projection cannot satisfy this required key.
                    return missingRequired(rootUri, typeRequired);
                }
            }
        }
        List<SchemaCheck.DependentRequired> dependencies = new ArrayList<SchemaCheck.DependentRequired>();
        if (root.containsKey("dependentRequired")) {
            Boolean supported = SchemaCompiler.supportsDependentRequired(snapshot, rootUri, root);
            if (supported == null) return null;
            if (supported) {
                Object rawDependencies = root.getNode("dependentRequired");
                if (JsonType.of(rawDependencies) != JsonType.OBJECT) return null;
                JsonObject dependencyMap = Nodes.toJsonObject(rawDependencies);
                for (String trigger : dependencyMap.keySet()) {
                    Object rawNames = dependencyMap.getNode(trigger);
                    if (JsonType.of(rawNames) != JsonType.ARRAY) return null;
                    List<String> names = new ArrayList<String>();
                    for (int i = 0; i < Nodes.sizeInArray(rawNames); i++) {
                        Object name = Nodes.getInArray(rawNames, i);
                        if (!(name instanceof String) || names.contains(name)) return null;
                        names.add((String) name);
                    }
                    if (dynamic) {
                        if (!names.isEmpty())
                            dependencies.add(new SchemaCheck.DependentRequired(trigger, names));
                    } else if (javaProperties.containsKey(trigger)) {
                        for (String name : names) {
                            if (!javaProperties.containsKey(name))
                                return missingObjectConstraint(rootUri, typeRequired, "dependentRequired");
                        }
                    }
                }
            }
        }
        Object additional = root.getNode("additionalProperties");
        SchemaCheck additionalCheck = null;
        if (root.containsKey("additionalProperties")) {
            if (additional instanceof Boolean) {
                if (!dynamic && Boolean.FALSE.equals(additional)
                        && !propertyMap.keySet().containsAll(javaProperties.keySet())) {
                    // The static projection is complete and the uncovered name
                    // must fail. JSON type applicability is enforced outside.
                    return missingObjectConstraint(rootUri, typeRequired, "additionalProperties");
                }
            } else if (JsonType.of(additional) == JsonType.OBJECT) {
                additionalCheck = schemaCompiler.compileValue(snapshot, additional,
                        SchemaValue.of(context, null),
                        SchemaCompiler.keywordUri(rootUri, "additionalProperties"), depth + 1);
                if (additionalCheck == null || additionalCheck.hasFallback()) return null;
            } else {
                return null;
            }
        }
        for (String name : propertyMap.keySet()) {
            if (!dynamic && !javaProperties.containsKey(name) && isRequired(required, name))
                return missingObjectConstraint(rootUri, typeRequired, "required");
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
                if (!dynamic && (property == null || property.read() == null))
                    return new SchemaCheck.Reject(SchemaCompiler.diagnostic(rootUri, "required"));
                // Primitive members cannot be null, so a required check is redundant.
                String requiredAccess = property == null || property.read().type().getKind().isPrimitive()
                        ? null : property.read().memberName() + (property.read().isMethod() ? "()" : "");
                requiredChecks.add(new SchemaCheck.Required(name, requiredAccess));
            }
        }

        List<SchemaCheck.PropertyCheck> properties = new ArrayList<SchemaCheck.PropertyCheck>();
        // Includes built-in formats; unresolved runtime registrations stay dynamic.
        CompiledMetadata codecMetadata = dynamic ? null
                : context.metadata(context.types.typeElement(type));
        int directCount = (typeRequired && propertyMap.isEmpty() ? 1 : 0)
                + (propertyNamesCheck != null && propertyNamesCheck.kind != SchemaCheck.Kind.PASS ? 1 : 0)
                + (!dependentSchemaChecks.isEmpty() ? 1 : 0)
                + (additionalCheck != null ? 1 : 0)
                + (!patternChecks.isEmpty() ? 1 : 0)
                + (required != null ? 1 : 0)
                + (!dependencies.isEmpty() ? 1 : 0)
                + (dynamic && Boolean.FALSE.equals(additional) ? 1 : 0)
                + (minProperties != null || maxProperties != null ? 1 : 0);
        for (String name : propertyMap.keySet()) {
            Property property = dynamic ? null : javaProperties.get(name);
            if (!dynamic && property == null) continue;
            TypeMirror declared = property == null ? null : property.read().type();
            String access = property == null ? null
                    : property.read().memberName() + (property.read().isMethod() ? "()" : "");
            String uri = childPointer(rootUri, "properties", name);
            boolean explicitCodec = property != null && property.explicitCodec();
            Object propertySchema = propertyMap.getNode(name);
            // Property-specific codecs override type defaults. Compile scalar
            // assertions against their raw encoding, not the Java domain type.
            // Structural keywords must remain on the original parent path:
            // a codec's raw Map/List is an atomic value, not an OBNT container.
            if (explicitCodec && !supportsCodecProperty(propertySchema)) return null;
            SchemaValue value = explicitCodec
                    ? encodedValue(declared, property, codecMetadata)
                    : SchemaValue.of(context, declared);
            SchemaCheck check = schemaCompiler.compileValue(snapshot, propertySchema, value, uri, depth + 1);
            // A fallback on just the child value loses @NodeProperty codec selection.
            if (explicitCodec && (check == null || check.hasFallback())) return null;
            if (check == null) check = new SchemaCheck.Fallback(uri);
            else directCount++;
            // A trivially valid subschema has no assertions to emit or property to read.
            if (check.kind == SchemaCheck.Kind.PASS) continue;
            properties.add(new SchemaCheck.PropertyCheck(name, access, value, check,
                    explicitCodec ? context.typeUtils.erasure(type).toString() : null));
        }
        // A child requiring fallback must remain visible even when no direct
        // checks were emitted. It is never a trivially valid object schema.
        if (directCount == 0 && properties.isEmpty()) return new SchemaCheck.Pass();
        // JOJO inherits the public JsonObject operations, so generated code
        // can bypass generic Nodes dispatch while preserving dynamic keys.
        boolean jsonObject = dynamic && type != null && (context.types.nodeKind(type) == NodeKind.OBJECT_JSON_OBJECT
                || context.types.nodeKind(type) == NodeKind.OBJECT_JOJO);
        return new SchemaCheck.ObjectCheck(dynamic, jsonObject, typeRequired,
                Boolean.FALSE.equals(additional), additionalCheck, patternChecks,
                propertyNamesCheck, dependentSchemaChecks,
                SchemaValue.of(context, null), minProperties, maxProperties, dynamic ? -1 : javaProperties.size(),
                new ArrayList<String>(propertyMap.keySet()), requiredChecks, dependencies, properties,
                typeRequired ? SchemaCompiler.diagnostic(rootUri, "type") : null,
                minProperties == null ? null : SchemaCompiler.diagnostic(rootUri, "minProperties"),
                maxProperties == null ? null : SchemaCompiler.diagnostic(rootUri, "maxProperties"),
                required == null ? null : SchemaCompiler.diagnostic(rootUri, "required"),
                dependencies.isEmpty() ? null : SchemaCompiler.diagnostic(rootUri, "dependentRequired"),
                Boolean.FALSE.equals(additional)
                        ? SchemaCompiler.diagnostic(rootUri, "additionalProperties") : null,
                propertyNamesCheck == null ? null : SchemaCompiler.diagnostic(rootUri, "propertyNames"));
    }

    /**
     * Object-only keywords do not apply to null. A final POJO with a missing
     * required property can only satisfy a type-less schema when it is null.
     */
    private static SchemaCheck missingRequired(String uri, boolean typeRequired) {
        return missingObjectConstraint(uri, typeRequired, "required");
    }

    private static SchemaCheck missingObjectConstraint(String uri, boolean typeRequired, String keyword) {
        SchemaCheck.Diagnostic diagnostic = SchemaCompiler.diagnostic(uri, keyword);
        if (typeRequired) return new SchemaCheck.Reject(diagnostic);
        return new SchemaCheck.TypeCheck(java.util.Collections.singletonList("null"), diagnostic);
    }

    /**
     * Select the raw scalar shape at compile time when the codec declaration
     * proves it. Unknown/runtime-installed codecs continue through DYNAMIC.
     * Map/List raw types are never converted into structural SchemaValue shapes.
     */
    private SchemaValue encodedValue(TypeMirror domain, Property property, CompiledMetadata metadata) {
        if (domain == null) return SchemaValue.of(context, null);
        TypeMirror raw = null;
        if (metadata != null) {
            // A pattern always selects the default codec, regardless of valueFormat.
            String format = property.valuePattern() == null ? property.valueFormat() : null;
            org.sjf4j.processor.discovery.ValueCodecMetadata codec = metadata.valueCodec(domain, format);
            if (codec != null) raw = codec.rawType;
        }
        if (raw == null && context.types.isNodeValue(domain)) {
            // Only @NodeValue's own encoder has a statically declared raw type.
            // A named alternative cannot be inferred from that default encoder.
            if (property.valuePattern() == null
                    && (property.valueFormat() == null || property.valueFormat().isEmpty())) {
                raw = context.types.nodeValueRawType(domain);
            }
        }
        if (raw != null && !raw.getKind().isPrimitive()) {
            SchemaValue value = SchemaValue.of(context, raw);
            if ((value.shape == SchemaValue.Shape.STRING
                    && "java.lang.String".equals(raw.toString()))
                    || value.shape == SchemaValue.Shape.NUMBER
                    || value.shape == SchemaValue.Shape.BOOLEAN) {
                return value;
            }
        }
        return SchemaValue.of(context, null);
    }

    /**
     * A raw encoded value can be inspected with scalar/literal checks and
     * applicators whose branches remain scalar-only. Object/array traversal
     * would violate the atomic @NodeValue semantics and is not permitted.
     */
    private static boolean supportsCodecProperty(Object source) {
        if (source instanceof Boolean) return true;
        if (JsonType.of(source) != JsonType.OBJECT) return false;
        JsonObject schema = Nodes.toJsonObject(source);
        for (String key : schema.keySet()) {
            if (SchemaCompiler.isAnnotation(key)) continue;
            switch (key) {
                case "type":
                    Object rawType = schema.getNode(key);
                    if (rawType instanceof String) {
                        if ("object".equals(rawType) || "array".equals(rawType)) return false;
                    } else if (JsonType.of(rawType) == JsonType.ARRAY) {
                        for (int i = 0; i < Nodes.sizeInArray(rawType); i++) {
                            Object item = Nodes.getInArray(rawType, i);
                            if (!(item instanceof String) || "object".equals(item)
                                    || "array".equals(item)) return false;
                        }
                    } else return false;
                    break;
                case "minLength": case "maxLength": case "pattern": case "format":
                case "minimum": case "maximum": case "exclusiveMinimum":
                case "exclusiveMaximum": case "multipleOf":
                case "const": case "enum":
                    break;
                case "allOf": case "anyOf": case "oneOf":
                    Object branches = schema.getNode(key);
                    if (JsonType.of(branches) != JsonType.ARRAY) return false;
                    for (int i = 0; i < Nodes.sizeInArray(branches); i++) {
                        if (!supportsCodecProperty(Nodes.getInArray(branches, i))) return false;
                    }
                    break;
                case "not": case "if": case "then": case "else":
                    if (!supportsCodecProperty(schema.getNode(key))) return false;
                    break;
                default: return false;
            }
        }
        return true;
    }

    /**
     * Only a public final POJO with an unambiguous public projection can use
     * static property access. Everything else uses Nodes, including interfaces,
     * extensible classes, maps and Object-typed values. Dynamic access is not
     * a SchemaPlan fallback.
     */
    private boolean useDynamicAccess(TypeMirror type) {
        if (type == null) return true;
        if ("java.lang.Object".equals(type.toString())) return true;
        NodeKind kind = context.types.nodeKind(type);
        // Scalar and array declarations cannot be treated as dynamic objects;
        // in particular their generated expressions may be primitive values.
        // A codec-backed value is never a traversable object, even when its
        // raw encoding is Map/List. Generate a JsonType.of guard for these
        // declarations instead of treating them as static POJOs.
        if (kind.isValueCodec()) return true;
        if (kind.isValue() || kind.isArray()) return false;
        // Compile-time NodeKind detects annotated @NodeValue methods; also
        // check known @NodeValue codec registry declarations (e.g. Instant).
        // Otherwise a final codec-backed Java class might be compiled as a POJO.
        TypeElement registered = context.types.typeElement(type);
        if (kind == NodeKind.OBJECT_POJO && registered != null
                && context.metadata(registered).hasValueCodec(type)) return true;
        if (kind != NodeKind.OBJECT_POJO) return true;
        TypeElement owner = context.types.typeElement(type);
        return owner == null || !owner.getModifiers().contains(Modifier.PUBLIC)
                || !owner.getModifiers().contains(Modifier.FINAL)
                || owner.getAnnotation(org.sjf4j.annotation.node.NodeObject.class) != null
                || hasHiddenNodeMembers(type);
    }

    private boolean hasHiddenNodeMembers(TypeMirror type) {
        TypeElement owner = context.types.typeElement(type);
        for (TypeElement current = owner; current != null; ) {
            for (javax.lang.model.element.Element member : current.getEnclosedElements()) {
                if (member.getModifiers().contains(Modifier.STATIC)) continue;
                if (member.getAnnotation(org.sjf4j.annotation.node.NodeProperty.class) != null
                        && !member.getModifiers().contains(Modifier.PUBLIC)
                        && !accessibleGetterForAnnotatedField(type, member)) return true;
                if (member.getAnnotation(org.sjf4j.annotation.node.NodeIgnore.class) != null) return true;
            }
            TypeMirror parent = current.getSuperclass();
            current = parent.getKind() == javax.lang.model.type.TypeKind.NONE
                    ? null : context.types.typeElement(parent);
        }
        return false;
    }

    /**
     * An annotated private backing field is visible through its public bean
     * getter when both belong to the same runtime property family.
     * The generated code uses the getter, never the private field.
     */
    private boolean accessibleGetterForAnnotatedField(TypeMirror owner,
            javax.lang.model.element.Element field) {
        String javaName = field.getSimpleName().toString();
        String mappedName = context.annotations.explicitPropertyName(field);
        org.sjf4j.processor.property.Property property = context.properties.resolve(owner)
                .get(mappedName == null ? javaName : mappedName);
        if (property == null || property.read() == null || !property.read().isMethod()) return false;
        // An ignored accessor cannot provide the runtime-visible property.
        javax.lang.model.element.Element getter = property.read().member();
        if (getter.getAnnotation(org.sjf4j.annotation.node.NodeIgnore.class) != null) return false;
        String accessor = property.read().memberName();
        if (accessor.equals(javaName)) return true; // record component
        String suffix = javaName.isEmpty() ? javaName
                : Character.toUpperCase(javaName.charAt(0)) + javaName.substring(1);
        return accessor.equals("get" + suffix) || accessor.equals("is" + suffix);
    }

    private static String pointerEscape(String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }

    private static String childPointer(String base, String keyword, String name) {
        return base + (base.contains("#") ? "/" : "#/")
                + pointerEscape(keyword) + "/" + encodeFragmentSegment(pointerEscape(name));
    }

    /** URI-encode a JSON Pointer segment without changing its RFC 6901 escaping. */
    private static String encodeFragmentSegment(String segment) {
        try {
            // URI's multi-argument constructor escapes quotes, whitespace and
            // reserved percent signs; toASCIIString also encodes non-ASCII.
            return new java.net.URI(null, null, "/" + segment, null)
                    .toASCIIString().substring(1);
        } catch (java.net.URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid schema property name", ex);
        }
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
