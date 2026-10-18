package org.sjf4j.processor.creator;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Standard compiled construction rules shared by mapping and binding.
 *
 * <p>Selection preserves the existing mapper contract: record canonical
 * constructor, public no-args constructor, or exactly one public constructor.
 * Annotation-specific overrides belong to the calling compiler.</p>
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
