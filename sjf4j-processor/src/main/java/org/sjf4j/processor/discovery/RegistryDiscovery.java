package org.sjf4j.processor.discovery;

import org.sjf4j.annotation.node.NodeRegistry;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.value.ValueCodec;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Discovers source registry interfaces and explicitly selected dependency holders. */
public final class RegistryDiscovery {

    private final ProcessorContext context;
    private final Map<String, TypeElement> sourceRegistries = new LinkedHashMap<>();

    public RegistryDiscovery(ProcessorContext context) {
        this.context = context;
    }

    /** No class loading or initialization occurs during discovery. */
    public void collect(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(NodeRegistry.class)) {
            if (element.getKind() != ElementKind.INTERFACE) {
                context.error(element, "@NodeRegistry must annotate an interface");
                continue;
            }
            TypeElement holder = (TypeElement) element;
            sourceRegistries.put(holder.getQualifiedName().toString(), holder);
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(NodeValue.class)) {
            if (element.getKind() != ElementKind.FIELD && element.getKind() != ElementKind.METHOD) continue;
            Element owner = element.getEnclosingElement();
            if (owner instanceof TypeElement && owner.getAnnotation(NodeRegistry.class) == null) {
                context.error(element, "@NodeValue codec declaration requires @NodeRegistry on "
                        + ((TypeElement) owner).getQualifiedName());
            }
        }
    }

    /** Built-ins, same-compilation declarations, then explicit dependency registries. */
    public List<TypeElement> registries(TypeElement owner) {
        Map<String, TypeElement> result = new LinkedHashMap<>();
        TypeElement builtins = context.elements.getTypeElement(ValueCodec.class.getName());
        if (builtins != null) result.put(builtins.getQualifiedName().toString(), builtins);

        List<String> names = new ArrayList<>(sourceRegistries.keySet());
        Collections.sort(names);
        for (String name : names) result.putIfAbsent(name, sourceRegistries.get(name));
        for (TypeElement holder : explicitRegistries(owner)) {
            result.putIfAbsent(holder.getQualifiedName().toString(), holder);
        }
        return new ArrayList<>(result.values());
    }

    private List<TypeElement> explicitRegistries(TypeElement owner) {
        List<TypeElement> registries = new ArrayList<>();
        for (AnnotationMirror annotation : owner.getAnnotationMirrors()) {
            String name = annotation.getAnnotationType().asElement().toString();
            if (!name.equals("org.sjf4j.annotation.binding.CompiledBinder")
                    && !name.equals("org.sjf4j.annotation.mapping.CompiledMapper")) continue;
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                    context.elements.getElementValuesWithDefaults(annotation).entrySet()) {
                if (!entry.getKey().getSimpleName().contentEquals("registries")) continue;
                for (Object item : (List<?>) entry.getValue().getValue()) {
                    TypeMirror mirror = (TypeMirror) ((AnnotationValue) item).getValue();
                    Element element = context.typeUtils.asElement(mirror);
                    if (!(element instanceof TypeElement)) {
                        context.error(owner, "cannot resolve @NodeRegistry interface '" + mirror + "'");
                        continue;
                    }
                    TypeElement holder = (TypeElement) element;
                    if (holder.getKind() != ElementKind.INTERFACE
                            || holder.getAnnotation(NodeRegistry.class) == null) {
                        context.error(owner, "registries requires an @NodeRegistry interface: "
                                + holder.getQualifiedName());
                        continue;
                    }
                    registries.add(holder);
                }
            }
        }
        return registries;
    }
}
