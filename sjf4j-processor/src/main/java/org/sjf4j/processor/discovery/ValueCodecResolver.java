package org.sjf4j.processor.discovery;

import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.processor.ProcessorContext;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves annotated codec fields using compiler model types only. */
public final class ValueCodecResolver {
    private final ProcessorContext context;
    private final List<TypeElement> registries;
    private final TypeMirror codecType;
    private final Map<String, Map<String, ValueCodecMetadata>> byType = new LinkedHashMap<>();
    private boolean scanned;

    public ValueCodecResolver(ProcessorContext context, List<TypeElement> registries) {
        this.context = context;
        this.registries = registries;
        this.codecType = context.elements.getTypeElement("org.sjf4j.value.ValueCodec").asType();
    }

    /** True if a codec is registered for this type or its nearest ancestor. */
    public boolean contains(TypeMirror valueType) {
        ensureScanned();
        return registrations(valueType) != null;
    }

    private void ensureScanned() {
        if (!scanned) {
            scanned = true;
            scan();
        }
    }

    public ValueCodecMetadata resolve(TypeMirror valueType, String format) {
        ensureScanned();
        Map<String, ValueCodecMetadata> codecs = registrations(valueType);
        if (codecs == null) return null;
        if (format == null) return codecs.values().iterator().next();
        return codecs.get(format);
    }

    /**
     * Runtime ValueRegistry resolves registered types breadth-first through
     * supertypes. Match that precedence, including ambiguity at equal distance.
     */
    private Map<String, ValueCodecMetadata> registrations(TypeMirror valueType) {
        ArrayDeque<TypeMirror> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(valueType);
        visited.add(context.typeUtils.erasure(valueType).toString());

        while (!queue.isEmpty()) {
            Map<String, ValueCodecMetadata> found = null;
            String matched = null;
            int breadth = queue.size();
            for (int i = 0; i < breadth; i++) {
                TypeMirror candidate = queue.remove();
                String key = context.typeUtils.erasure(candidate).toString();
                Map<String, ValueCodecMetadata> codecs = byType.get(key);
                if (codecs != null) {
                    if (found != null) {
                        TypeElement element = context.types.typeElement(valueType);
                        if (element != null) {
                            context.error(element, "ambiguous @NodeValue codecs for '"
                                    + valueType + "': registered for '" + matched
                                    + "' and '" + key + "' at the same type distance");
                        }
                        return found;
                    }
                    found = codecs;
                    matched = key;
                }
                for (TypeMirror parent : context.typeUtils.directSupertypes(candidate)) {
                    String name = context.typeUtils.erasure(parent).toString();
                    if (visited.add(name)) queue.add(parent);
                }
            }
            if (found != null) return found;
        }
        return null;
    }

    private void scan() {
        for (TypeElement holder : registries) {
            for (Element element : holder.getEnclosedElements()) {
                if (element.getKind() != ElementKind.FIELD
                        || element.getAnnotation(NodeValue.class) == null) continue;
                ValueCodecMetadata metadata = parse((VariableElement) element);
                if (metadata == null) continue;
                String typeName = context.typeUtils.erasure(metadata.valueType).toString();
                Map<String, ValueCodecMetadata> formats =
                        byType.computeIfAbsent(typeName, k -> new LinkedHashMap<>());
                for (String format : metadata.formats) {
                    ValueCodecMetadata previous = formats.putIfAbsent(format, metadata);
                    if (previous != null) {
                        context.error(metadata.declaration, "duplicate @NodeValue codec format '"
                                + format + "' for type '" + typeName + "'; previously declared by "
                                + previous.declaration);
                    }
                }
            }
        }
    }

    /** Validates a declaration without loading or initializing its codec instance. */
    private ValueCodecMetadata parse(VariableElement field) {
        if (!field.getModifiers().containsAll(Arrays.asList(
                Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL))
                || !context.typeUtils.isAssignable(
                        context.typeUtils.erasure(field.asType()),
                        context.typeUtils.erasure(codecType))) {
            context.error(field, "@NodeValue codec field must be public static final ValueCodec<V, R>");
            return null;
        }

        if (!(field.asType() instanceof DeclaredType)) {
            context.error(field, "@NodeValue codec field must declare concrete V/R classes");
            return null;
        }
        List<? extends TypeMirror> arguments = ((DeclaredType) field.asType()).getTypeArguments();
        if (arguments.size() != 2 || !concreteClass(arguments.get(0))
                || !concreteClass(arguments.get(1))) {
            context.error(field, "@NodeValue codec field must declare concrete V/R classes");
            return null;
        }

        List<String> formats = Arrays.asList(field.getAnnotation(NodeValue.class).formats());
        if (formats.isEmpty()) {
            context.error(field, "@NodeValue codec field must declare at least one format");
            return null;
        }
        return new ValueCodecMetadata(arguments.get(0), arguments.get(1), formats, field);
    }

    private boolean concreteClass(TypeMirror type) {
        return type instanceof DeclaredType
                && ((DeclaredType) type).getTypeArguments().isEmpty()
                && ((DeclaredType) type).asElement() instanceof TypeElement;
    }
}
