package org.sjf4j.processor.discovery;

import org.sjf4j.annotation.binding.BindingOptions;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.processor.ProcessorContext;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Metadata visible to one generated interface, independent of runtime registries. */
public final class CompiledMetadata {
    private final ValueCodecResolver valueCodecs;
    private final boolean staticBinding;
    private final Map<String, String> selectedFormats;

    public CompiledMetadata(ProcessorContext context, TypeElement owner) {
        this.valueCodecs = new ValueCodecResolver(context, context.discovery.registries(owner));
        // All CompiledBinder interfaces use static value-codec resolution.
        // @BindingOptions only supplies optional overrides; it is not an
        // opt-in switch for compiled semantics.
        this.staticBinding = owner.getAnnotation(CompiledBinder.class) != null;
        this.selectedFormats = selections(context, owner);
    }

    /** A configured CompiledBinder resolves its codecs without a Runtime ValueRegistry. */
    public boolean staticBinding() {
        return staticBinding;
    }

    public boolean hasValueCodec(TypeMirror type) {
        return valueCodecs.contains(type);
    }

    public ValueCodecMetadata valueCodec(TypeMirror type, String format) {
        return valueCodecs.resolve(type, format == null ? selectedFormat(type) : format);
    }

    public List<ValueCodecMetadata> valueCodecs(TypeMirror type) {
        return valueCodecs.all(type);
    }

    public TypeMirror stableCodecRawType(TypeMirror type, String format) {
        return valueCodecs.stableRawType(type, format == null ? selectedFormat(type) : format);
    }

    public String selectedFormat(TypeMirror type) {
        return selectedFormats.get(type.toString());
    }

    private static Map<String, String> selections(ProcessorContext context, TypeElement owner) {
        Map<String, String> mappings = new LinkedHashMap<>();
        for (AnnotationMirror annotation : owner.getAnnotationMirrors()) {
            if (!annotation.getAnnotationType().toString().equals(BindingOptions.class.getName())) continue;
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                    context.elements.getElementValuesWithDefaults(annotation).entrySet()) {
                if (!entry.getKey().getSimpleName().contentEquals("valueCodecs")) continue;
                for (Object element : (List<?>) entry.getValue().getValue()) {
                    AnnotationMirror mapping = (AnnotationMirror) ((AnnotationValue) element).getValue();
                    TypeMirror type = null;
                    String format = null;
                    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> field :
                            context.elements.getElementValuesWithDefaults(mapping).entrySet()) {
                        String name = field.getKey().getSimpleName().toString();
                        if (name.equals("type")) type = (TypeMirror) field.getValue().getValue();
                        if (name.equals("format")) format = (String) field.getValue().getValue();
                    }
                    if (type == null || format == null) {
                        context.error(owner, "Invalid @CodecMapping declaration");
                        continue;
                    }
                    String prior = mappings.putIfAbsent(type.toString(), format);
                    if (prior != null) {
                        context.error(owner, "Duplicate @CodecMapping for " + type);
                    }
                }
            }
        }
        return Collections.unmodifiableMap(mappings);
    }
}
