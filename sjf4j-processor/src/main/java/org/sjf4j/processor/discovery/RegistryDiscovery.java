package org.sjf4j.processor.discovery;

import org.sjf4j.annotation.node.NodeRegistry;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.external.ExternalNode;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.value.ValueCodec;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds interface-based node registries without loading or initializing them.
 * Source registries are discovered automatically; precompiled registries must
 * be supplied explicitly on the compiled interface.
 */
public final class RegistryDiscovery {
    private final ProcessorContext context;
    private final Map<String, TypeElement> sourceRegistries = new LinkedHashMap<>();

    public RegistryDiscovery(ProcessorContext context) {
        this.context = context;
    }

    public void collect(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(NodeRegistry.class)) {
            if (element instanceof TypeElement && element.getKind() == ElementKind.INTERFACE) {
                TypeElement type = (TypeElement) element;
                sourceRegistries.put(type.getQualifiedName().toString(), type);
            } else {
                context.error(element, "@NodeRegistry must annotate an interface");
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(NodeValue.class)) {
            if (element.getKind() != ElementKind.FIELD && element.getKind() != ElementKind.METHOD) {
                continue;
            }
            Element holder = element.getEnclosingElement();
            if (holder instanceof TypeElement && holder.getAnnotation(NodeRegistry.class) == null) {
                context.error(element, "@NodeValue codec declaration requires @NodeRegistry on "
                        + ((TypeElement) holder).getQualifiedName());
            }
        }
    }

    /** Built-in and source registries followed by explicitly named dependencies. */
    public List<TypeElement> registries(TypeElement owner) {
        Map<String, TypeElement> result = new LinkedHashMap<>();
        TypeElement builtins = context.elements.getTypeElement(ValueCodec.class.getName());
        if (builtins != null) result.put(builtins.getQualifiedName().toString(), builtins);

        List<String> names = new ArrayList<>(sourceRegistries.keySet());
        Collections.sort(names);
        for (String name : names) result.putIfAbsent(name, sourceRegistries.get(name));
        for (TypeElement registry : explicitRegistries(owner)) {
            result.putIfAbsent(registry.getQualifiedName().toString(), registry);
        }
        return new ArrayList<>(result.values());
    }

    /** External root types visible to an individual compiled interface. */
    public List<TypeMirror> externalTypes(TypeElement owner) {
        TypeElement base = context.elements.getTypeElement(ExternalNode.class.getName());
        if (base == null) return Collections.emptyList();
        TypeMirror erased = context.typeUtils.erasure(base.asType());
        Map<String, TypeMirror> result = new LinkedHashMap<>();
        for (TypeElement registry : registries(owner)) {
            if (!validRegistry(registry)) continue;
            for (Element member : registry.getEnclosedElements()) {
                TypeMirror candidate = null;
                if (member.getKind() == ElementKind.FIELD) {
                    if (!member.getModifiers().contains(Modifier.STATIC)) continue;
                    candidate = member.asType();
                } else if (member.getKind() == ElementKind.METHOD) {
                    ExecutableElement method = (ExecutableElement) member;
                    if (!member.getModifiers().contains(Modifier.STATIC)
                            || !method.getParameters().isEmpty()) continue;
                    candidate = method.getReturnType();
                }
                if (candidate == null || !(candidate instanceof DeclaredType)
                        || !context.typeUtils.isSameType(
                                context.typeUtils.erasure(candidate), erased)) continue;
                List<? extends TypeMirror> arguments = ((DeclaredType) candidate).getTypeArguments();
                if (arguments.size() != 1 || !(arguments.get(0) instanceof DeclaredType)
                        || !((DeclaredType) arguments.get(0)).getTypeArguments().isEmpty()) {
                    context.error(member, "ExternalNode declaration must specify a concrete root class");
                    continue;
                }
                TypeMirror root = arguments.get(0);
                result.putIfAbsent(context.typeUtils.erasure(root).toString(), root);
            }
        }
        return new ArrayList<>(result.values());
    }

    private boolean validRegistry(TypeElement registry) {
        if (registry.getKind() == ElementKind.INTERFACE
                && registry.getAnnotation(NodeRegistry.class) != null) return true;
        context.error(registry, "registries requires an @NodeRegistry interface: "
                + registry.getQualifiedName());
        return false;
    }

    private List<TypeElement> explicitRegistries(TypeElement owner) {
        List<TypeElement> registries = new ArrayList<>();
        if (owner == null) return registries;
        for (AnnotationMirror annotation : owner.getAnnotationMirrors()) {
            String name = annotation.getAnnotationType().asElement().toString();
            if (!name.equals("org.sjf4j.annotation.binding.CompiledBinder")
                    && !name.equals("org.sjf4j.annotation.mapping.CompiledMapper")
                    && !name.equals("org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper")
                    && !name.equals("org.sjf4j.annotation.schema.CompiledValidator")) continue;
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                    context.elements.getElementValuesWithDefaults(annotation).entrySet()) {
                if (!entry.getKey().getSimpleName().contentEquals("registries")) continue;
                for (Object item : (List<?>) entry.getValue().getValue()) {
                    TypeMirror type = (TypeMirror) ((AnnotationValue) item).getValue();
                    Element element = context.typeUtils.asElement(type);
                    if (element instanceof TypeElement) {
                        TypeElement holder = (TypeElement) element;
                        if (validRegistry(holder)) registries.add(holder);
                    } else {
                        context.error(owner, "cannot resolve node registry '" + type + "'");
                    }
                }
            }
        }
        return registries;
    }
}
