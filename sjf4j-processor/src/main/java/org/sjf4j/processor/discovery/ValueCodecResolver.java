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
import java.util.Arrays;
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

    /** True if any format is declared for this value type. */
    public boolean contains(TypeMirror valueType) {
        ensureScanned();
        return byType.containsKey(context.typeUtils.erasure(valueType).toString());
    }

    private void ensureScanned() {
        if (!scanned) {
            scanned = true;
            scan();
        }
    }

    public ValueCodecMetadata resolve(TypeMirror valueType, String format) {
        ensureScanned();
        Map<String, ValueCodecMetadata> codecs =
                byType.get(context.typeUtils.erasure(valueType).toString());
        return codecs == null ? null : codecs.get(format == null ? "" : format);
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
