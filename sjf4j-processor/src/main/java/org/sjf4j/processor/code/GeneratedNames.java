package org.sjf4j.processor.code;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import java.util.Objects;

/**
 * Owns identifiers for one generated implementation.
 *
 * <p>Generated fields, private helper methods and nested types share a
 * conservative class-level namespace. Method locals use a fresh
 * {@link NameAllocator} with the source parameters reserved first. Each
 * allocation keeps the first suggested name, appending 2, 3, ... only on
 * collision.</p>
 *
 * <p>Allocation belongs to the analysis/compilation phase. Emitters should
 * reuse the allocated identifier rather than derive related names by
 * concatenating strings.</p>
 */
public final class GeneratedNames {

    private final NameAllocator members = new NameAllocator();

    GeneratedNames(Elements elements, TypeElement origin, String implementationName) {
        Objects.requireNonNull(elements, "elements");
        Objects.requireNonNull(origin, "origin");

        // Fixed members emitted by GeneratedClass and BindingGenerator.
        members.reserve(implementationName).reserve("context").reserve("binder");

        // Include inherited and default methods, not just abstract methods.
        // The conservative shared namespace also avoids field/method/type
        // shadowing in the generated source.
        for (Element member : elements.getAllMembers(origin)) {
            ElementKind kind = member.getKind();
            if (kind == ElementKind.METHOD || kind.isField()
                    || kind.isClass() || kind.isInterface()) {
                members.reserve(member.getSimpleName().toString());
            }
        }
    }

    /**
     * Reserves an already-defined or fixed class member, including members
     * emitted by code generators not yet using this allocator.
     */
    public GeneratedNames reserveMember(String name) {
        members.reserve(name);
        return this;
    }

    /** Allocates a generated class field name. */
    public String field(String suggestion) {
        return members.newName(suggestion);
    }

    /**
     * Allocates a private helper method name, always beginning with '_'.
     * An existing leading underscore is preserved.
     */
    public String helperMethod(String suggestion) {
        String hint = suggestion == null || suggestion.isEmpty() ? "helper" : suggestion;
        return members.newName(hint.charAt(0) == '_' ? hint : "_" + hint);
    }

    /** Allocates a generated nested type name. */
    public String type(String suggestion) {
        return members.newName(suggestion);
    }

    /** Creates an independent local namespace with source parameters reserved. */
    public NameAllocator locals(ExecutableElement method) {
        Objects.requireNonNull(method, "method");
        NameAllocator locals = new NameAllocator();
        for (VariableElement parameter : method.getParameters()) {
            locals.reserve(parameter.getSimpleName().toString());
        }
        return locals;
    }

    /** Creates an independent local namespace for a synthetic helper method. */
    public NameAllocator helperLocals(String... parameterNames) {
        NameAllocator locals = new NameAllocator();
        if (parameterNames != null) {
            for (String parameter : parameterNames) {
                locals.reserve(parameter);
            }
        }
        return locals;
    }
}
