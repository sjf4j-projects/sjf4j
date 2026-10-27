package org.sjf4j.processor.discovery;

import org.sjf4j.annotation.binding.BindingOptions;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.value.ValueCodec;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers registry holders. Only source declarations in the current processing
 * run can be enumerated; dependency classes require an explicit entry point.
 *
 * <p>This class discovers holder types, not codec instances. It never loads
 * application classes or executes initialization code.
 */
public final class RegistryDiscovery {

    private static final String BUILTIN_VALUE_CODECS = ValueCodec.class.getName();

    private final ProcessorContext context;
    private final Map<String, TypeElement> sourceRegistries = new LinkedHashMap<>();

    public RegistryDiscovery(ProcessorContext context) {
        this.context = context;
    }

    /** Collects registry holders declared in this compilation round. */
    public void collect(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(NodeValue.class)) {
            if (element.getKind() != ElementKind.FIELD) continue;
            Element holder = element.getEnclosingElement();
            if (holder instanceof TypeElement) {
                TypeElement type = (TypeElement) holder;
                sourceRegistries.put(type.getQualifiedName().toString(), type);
            }
        }
    }

    /** Built-in and source registries, with a stable order and no duplicates. */
    public List<TypeElement> registries(TypeElement owner) {
        Map<String, TypeElement> result = new LinkedHashMap<>();
        TypeElement builtins = context.elements.getTypeElement(BUILTIN_VALUE_CODECS);
        if (builtins != null) {
            result.put(builtins.getQualifiedName().toString(), builtins);
        }

        List<String> names = new ArrayList<>(sourceRegistries.keySet());
        Collections.sort(names);
        for (String name : names) {
            result.putIfAbsent(name, sourceRegistries.get(name));
        }
        // An explicit list is scoped to the annotated compiled interface.
        for (TypeElement registry : explicitRegistries(owner)) {
            result.putIfAbsent(registry.getQualifiedName().toString(), registry);
        }
        return new ArrayList<>(result.values());
    }

    private List<TypeElement> explicitRegistries(TypeElement owner) {
        List<TypeElement> registries = new ArrayList<>();
        for (AnnotationMirror annotation : owner.getAnnotationMirrors()) {
            Element declaration = annotation.getAnnotationType().asElement();
            if (!(declaration instanceof TypeElement)
                    || !((TypeElement) declaration).getQualifiedName()
                            .contentEquals(BindingOptions.class.getName())) {
                continue;
            }

            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                    context.elements.getElementValuesWithDefaults(annotation).entrySet()) {
                if (!entry.getKey().getSimpleName().contentEquals("registries")) continue;

                for (Object item : (List<?>) entry.getValue().getValue()) {
                    TypeMirror type = (TypeMirror) ((AnnotationValue) item).getValue();
                    Element element = context.typeUtils.asElement(type);
                    if (!(element instanceof TypeElement)) {
                        context.error(owner, "cannot resolve @BindingOptions registry class '" + type + "'");
                        continue;
                    }
                    registries.add((TypeElement) element);
                }
            }
        }
        return registries;
    }
}
