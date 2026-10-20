package org.sjf4j.processor.discovery;

import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.value.ValueCodec;
import org.sjf4j.processor.ProcessorContext;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
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
        this.codecType = context.elements.getTypeElement(ValueCodec.class.getName()).asType();
    }

    /** True if a codec is registered for this type or its nearest ancestor. */
    public boolean contains(TypeMirror valueType) {
        ensureScanned();
        return registrations(valueType) != null;
    }

    /**
     * A statically known raw type is usable for specialization only if all
     * selectable declared formats agree (or an explicit format selects one).
     * Generated writers still check ValueInfo.rawClazz: runtime registrations
     * and RuntimeContext defaults are not controlled by the processor.
     */
    public TypeMirror stableRawType(TypeMirror valueType, String format) {
        ensureScanned();
        Map<String, ValueCodecMetadata> codecs = registrations(valueType);
        if (codecs == null) return null;
        if (format != null) {
            ValueCodecMetadata selected = codecs.get(format);
            return selected == null ? null : selected.rawType;
        }
        TypeMirror raw = null;
        for (ValueCodecMetadata codec : codecs.values()) {
            if (raw == null) {
                raw = codec.rawType;
            } else if (!context.typeUtils.isSameType(
                    context.typeUtils.erasure(raw), context.typeUtils.erasure(codec.rawType))) {
                return null;
            }
        }
        return raw;
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
            // Match ValueRegistry.registerAnnotated(): designated defaults
            // precede named variants and declaration order is not significant.
            List<VariableElement> fields = new ArrayList<>();
            for (Element element : holder.getEnclosedElements()) {
                if (element.getKind() == ElementKind.FIELD
                        && element.getAnnotation(NodeValue.class) != null) {
                    fields.add((VariableElement) element);
                }
            }
            fields.sort(Comparator
                    .comparing((VariableElement field) -> {
                        NodeValue annotation = field.getAnnotation(NodeValue.class);
                        return !(annotation.defaultCodec() || annotation.format().isEmpty());
                    })
                    .thenComparing(field -> field.getSimpleName().toString()));
            for (VariableElement field : fields) {
                ValueCodecMetadata metadata = parse(field);
                if (metadata == null) continue;
                String typeName = context.typeUtils.erasure(metadata.valueType).toString();
                Map<String, ValueCodecMetadata> formats =
                        byType.computeIfAbsent(typeName, k -> new LinkedHashMap<>());
                ValueCodecMetadata previous = formats.putIfAbsent(metadata.format, metadata);
                if (previous != null) {
                    context.error(metadata.declaration, "duplicate @NodeValue codec format '"
                            + metadata.format + "' for type '" + typeName + "'; previously declared by "
                            + previous.declaration);
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

        String format = field.getAnnotation(NodeValue.class).format();
        return new ValueCodecMetadata(arguments.get(0), arguments.get(1), format, field);
    }

    private boolean concreteClass(TypeMirror type) {
        return type instanceof DeclaredType
                && ((DeclaredType) type).getTypeArguments().isEmpty()
                && ((DeclaredType) type).asElement() instanceof TypeElement;
    }
}
