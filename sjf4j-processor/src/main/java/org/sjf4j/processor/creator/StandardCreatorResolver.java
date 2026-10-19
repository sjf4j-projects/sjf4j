package org.sjf4j.processor.creator;

import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Standard compiled construction rules shared by mapping and binding.
 *
 * <p>Selection preserves the existing mapper contract: record canonical
 * constructor, public no-args constructor, or exactly one public constructor.
 * Binding-specific creator annotation selection is handled by resolveBinding.</p>
 */
public final class StandardCreatorResolver {

    private final ProcessorContext context;
    private final TypeSystem types;

    public StandardCreatorResolver(ProcessorContext context) {
        this.context = context;
        this.types = context.types;
    }

    /**
     * Resolves a standard construction strategy, reporting diagnostics through
     * the caller so that its generated class can be invalidated as appropriate.
     */
    public CreatorPlan resolve(TypeMirror declaredTarget, TypeMirror workingType, Element origin,
                               String explicitCreatorHint, Consumer<String> error) {
        workingType = types.concrete(workingType);
        TypeElement type = types.typeElement(workingType);

        if (type == null) {
            error.accept("cannot create target type " + workingType);
            return null;
        }
        if (type.getKind() == ElementKind.INTERFACE) {
            error.accept("target type " + workingType + " is an interface and requires " + explicitCreatorHint);
            return null;
        }
        if (type.getModifiers().contains(Modifier.ABSTRACT)) {
            error.accept("target type " + workingType + " is abstract and requires " + explicitCreatorHint);
            return null;
        }
        if (!isTypeAccessible(type, context.elements.getPackageOf(origin))) {
            error.accept("target type " + workingType + " is not accessible from generated code");
            return null;
        }

        List<ExecutableElement> constructors = constructors(type);
        if ("RECORD".equals(type.getKind().name())) {
            ExecutableElement canonical = recordConstructor(type, constructors);
            if (canonical == null) {
                error.accept("cannot resolve canonical constructor for record " + workingType);
                return null;
            }
            return constructorPlan(declaredTarget, workingType, canonical);
        }

        // An implicit no-args constructor is available when none is declared.
        if (constructors.isEmpty()) return CreatorPlan.noArgs(declaredTarget, workingType);

        List<ExecutableElement> publicConstructors = new ArrayList<>();
        for (ExecutableElement constructor : constructors) {
            if (!constructor.getModifiers().contains(Modifier.PUBLIC)) continue;
            if (constructor.getParameters().isEmpty()) {
                return CreatorPlan.noArgs(declaredTarget, workingType);
            }
            publicConstructors.add(constructor);
        }
        if (publicConstructors.size() == 1) {
            return constructorPlan(declaredTarget, workingType, publicConstructors.get(0));
        }

        error.accept("target type " + workingType +
                (publicConstructors.isEmpty()
                        ? " has no public constructor"
                        : " has multiple public constructors and no public no-args constructor"));
        return null;
    }

    /**
     * Binding-specific creator selection. Explicit @NodeCreator or Jackson 2/3
     * @JsonCreator wins over the no-args path. Unannotated argument constructors
     * remain unsupported; mapper creator selection is unchanged.
     */
    public CreatorPlan resolveBinding(TypeMirror targetType, Element origin,
                                      Consumer<String> error) {
        TypeMirror target = types.concrete(targetType);
        TypeElement type = types.typeElement(target);
        if (type == null || !isTypeAccessible(type, context.elements.getPackageOf(origin))) {
            error.accept("Creator target is not accessible from generated code: " + target);
            return null;
        }
        ExecutableElement selected = null;
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.CONSTRUCTOR
                    && enclosed.getKind() != ElementKind.METHOD) continue;
            ExecutableElement candidate = (ExecutableElement) enclosed;
            if (candidate.getAnnotation(NodeCreator.class) == null
                    && !context.annotations.hasJacksonCreator(candidate)) continue;
            if (selected != null) {
                error.accept("multiple creator definitions for " + target);
                return null;
            }
            selected = candidate;
        }
        if (selected == null) {
            CreatorPlan standard = resolve(target, target, origin, "@NodeCreator or @JsonCreator", error);
            if (standard == null) return null;
            if (standard.kind() != CreatorPlan.Kind.NO_ARGS
                    && !"RECORD".equals(type.getKind().name())) {
                error.accept("read binding requires a public no-args constructor or explicit @NodeCreator or @JsonCreator: " + target);
                return null;
            }
            return standard;
        }

        Set<Modifier> modifiers = selected.getModifiers();
        if (modifiers.contains(Modifier.PRIVATE)
                || !modifiers.contains(Modifier.PUBLIC)
                && !context.elements.getPackageOf(type).equals(context.elements.getPackageOf(origin))) {
            error.accept("Creator is not accessible from generated code: " + selected);
            return null;
        }
        if (selected.getKind() == ElementKind.CONSTRUCTOR) {
            return constructorPlan(target, target, selected);
        }
        if (!selected.getModifiers().contains(Modifier.STATIC)
                || selected.getReturnType().getKind() == TypeKind.VOID
                || !context.typeUtils.isAssignable(selected.getReturnType(), target)) {
            error.accept("Creator factory must be static and return " + target + ": " + selected);
            return null;
        }
        return CreatorPlan.staticFactory(target, target, selected,
                resolvedParameterTypes(target, selected));
    }

    private CreatorPlan constructorPlan(TypeMirror declaredType, TypeMirror workingType,
                                        ExecutableElement constructor) {
        return CreatorPlan.constructor(declaredType, workingType, constructor,
                resolvedParameterTypes(workingType, constructor));
    }

    private List<? extends TypeMirror> resolvedParameterTypes(TypeMirror owner, ExecutableElement constructor) {
        if (owner instanceof DeclaredType) {
            try {
                ExecutableType type = (ExecutableType) context.typeUtils.asMemberOf(
                        (DeclaredType) owner, constructor);
                return new ArrayList<>(type.getParameterTypes());
            } catch (IllegalArgumentException ignored) {
                // Fall through to the declared parameter types.
            }
        }
        List<TypeMirror> result = new ArrayList<>();
        for (VariableElement parameter : constructor.getParameters()) {
            result.add(parameter.asType());
        }
        return result;
    }

    private static List<ExecutableElement> constructors(TypeElement type) {
        List<ExecutableElement> result = new ArrayList<>();
        for (Element element : type.getEnclosedElements()) {
            if (element.getKind() == ElementKind.CONSTRUCTOR) {
                result.add((ExecutableElement) element);
            }
        }
        return result;
    }

    private static ExecutableElement recordConstructor(TypeElement type,
                                                        List<ExecutableElement> constructors) {
        List<Element> components = new ArrayList<>();
        for (Element element : type.getEnclosedElements()) {
            if ("RECORD_COMPONENT".equals(element.getKind().name())) {
                components.add(element);
            }
        }
        for (ExecutableElement constructor : constructors) {
            List<? extends VariableElement> parameters = constructor.getParameters();
            if (parameters.size() != components.size()) continue;
            boolean match = true;
            for (int i = 0; i < parameters.size(); i++) {
                if (!parameters.get(i).getSimpleName().contentEquals(components.get(i).getSimpleName())) {
                    match = false;
                    break;
                }
            }
            if (match) return constructor;
        }
        return null;
    }

    /** Checks that the target type and all enclosing types are accessible. */
    public boolean isTypeAccessible(TypeElement type, PackageElement generatedPackage) {
        for (Element current = type; current instanceof TypeElement; current = current.getEnclosingElement()) {
            TypeElement currentType = (TypeElement) current;
            Set<Modifier> modifiers = currentType.getModifiers();
            if (modifiers.contains(Modifier.PRIVATE)) return false;
            if (!modifiers.contains(Modifier.PUBLIC)
                    && !context.elements.getPackageOf(currentType).equals(generatedPackage)) {
                return false;
            }
        }
        return true;
    }
}
