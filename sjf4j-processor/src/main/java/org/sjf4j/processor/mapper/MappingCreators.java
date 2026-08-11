package org.sjf4j.processor.mapper;

import org.sjf4j.annotation.mapper.MappingCreator;
import org.sjf4j.processor.GeneratorUtil;
import org.sjf4j.processor.ProcessorContext;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Processor-time creator metadata shared by the independent mapper generators.
 * Collection and selection are shared; each generator retains its own target
 * construction and property-mapping plan.
 */
final class MappingCreators {
    interface Errors {
        void error(Element element, String message);
    }

    /** The selected concrete construction type and optional generated factory call. */
    static final class Match {
        final TypeMirror type;
        final String create;

        Match(TypeMirror type, String create) {
            this.type = type;
            this.create = create;
        }
    }

    /** A validated creator declaration, retaining its owner for static factories. */
    private static final class Ref {
        final TypeMirror target;
        final TypeMirror implementation;
        final String creator;
        final TypeElement owner;

        Ref(TypeMirror target, TypeMirror implementation, String creator, TypeElement owner) {
            this.target = target;
            this.implementation = implementation;
            this.creator = creator;
            this.owner = owner;
        }
    }

    private final ProcessorContext ctx;
    private final TypeElement iface;
    private final Errors errors;
    private final List<Ref> inherited = new ArrayList<Ref>();
    private final Map<ExecutableElement, List<Ref>> methods = new HashMap<ExecutableElement, List<Ref>>();
    private boolean failed;

    /** Collects interface-level creators, including declarations inherited from parent interfaces. */
    MappingCreators(ProcessorContext ctx, TypeElement iface, Errors errors) {
        this.ctx = ctx;
        this.iface = iface;
        this.errors = errors;
        collect(iface, inherited, new HashSet<String>());
    }

    boolean valid() {
        return !failed;
    }

    boolean validateMethod(ExecutableElement method) {
        return methodRefs(method) != null;
    }

    /**
     * Resolves a creator for one requested return type. Matching method-level
     * declarations take precedence over interface-level declarations.
     */
    Match forMethod(ExecutableElement method, TypeMirror requested) {
        List<Ref> local = methodRefs(method);
        if (local == null) return null;
        boolean[] matched = new boolean[1];
        Ref best = best(method, requested, local, matched);
        if (failed) return null;
        if (!matched[0]) best = best(method, requested, inherited, matched);
        if (failed || best == null) return null;
        return best.implementation != null ? new Match(best.implementation, null) : factory(method, best, requested);
    }

    private List<Ref> methodRefs(ExecutableElement method) {
        if (methods.containsKey(method)) return methods.get(method);
        List<Ref> refs = new ArrayList<Ref>();
        boolean before = failed;
        collectDeclared(method, refs);
        if (failed != before) return null;
        methods.put(method, refs);
        return refs;
    }

    private void collect(TypeElement type, List<Ref> refs, Set<String> seen) {
        if (!seen.add(type.getQualifiedName().toString())) return;
        collectDeclared(type, refs);
        for (TypeMirror parent : type.getInterfaces()) {
            TypeElement parentType = GeneratorUtil.asTypeElement(parent);
            if (parentType != null) collect(parentType, refs, seen);
        }
    }

    private void collectDeclared(Element owner, List<Ref> refs) {
        for (AnnotationMirror annotation : owner.getAnnotationMirrors()) {
            String name = annotation.getAnnotationType().toString();
            if (name.equals(MappingCreator.class.getName())) {
                add(owner, refs, annotation);
            } else if (name.equals("org.sjf4j.annotation.mapper.MappingCreators")) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                        : ctx.elements.getElementValuesWithDefaults(annotation).entrySet()) {
                    if (!entry.getKey().getSimpleName().contentEquals("value")
                            || !(entry.getValue().getValue() instanceof List)) continue;
                    for (Object item : (List<?>) entry.getValue().getValue()) {
                        Object value = ((AnnotationValue) item).getValue();
                        if (value instanceof AnnotationMirror) add(owner, refs, (AnnotationMirror) value);
                    }
                }
            }
        }
    }

    private void add(Element owner, List<Ref> refs, AnnotationMirror annotation) {
        TypeMirror target = null;
        TypeMirror implementation = null;
        String creator = "";
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                : ctx.elements.getElementValuesWithDefaults(annotation).entrySet()) {
            String name = entry.getKey().getSimpleName().toString();
            if (name.equals("targetType")) target = (TypeMirror) entry.getValue().getValue();
            else if (name.equals("implementation")) implementation = (TypeMirror) entry.getValue().getValue();
            else if (name.equals("creator")) creator = String.valueOf(entry.getValue().getValue()).trim();
        }
        if (target == null || target.getKind() != TypeKind.DECLARED) {
            fail(owner, "@MappingCreator.targetType must be a declared type");
            return;
        }
        boolean hasImplementation = implementation != null && !"java.lang.Void".equals(qualified(implementation));
        boolean hasCreator = !creator.isEmpty();
        if (hasImplementation == hasCreator) {
            fail(owner, "@MappingCreator requires exactly one of implementation or creator");
            return;
        }
        if (hasImplementation && (implementation.getKind() != TypeKind.DECLARED
                || !ctx.types.isAssignable(implementation, target))) {
            fail(owner, implementation.getKind() != TypeKind.DECLARED
                    ? "@MappingCreator.implementation must be a declared type"
                    : "@MappingCreator.implementation must be assignable to targetType");
            return;
        }
        Element enclosing = owner.getKind() == ElementKind.METHOD ? owner.getEnclosingElement() : owner;
        refs.add(new Ref(target, hasImplementation ? implementation : null,
                hasCreator ? creator : null, (TypeElement) enclosing));
    }

    private Ref best(ExecutableElement method, TypeMirror requested, List<Ref> refs, boolean[] matched) {
        matched[0] = false;
        Ref best = null;
        for (Ref ref : refs) {
            if (!ctx.types.isAssignable(requested, ref.target)) continue;
            matched[0] = true;
            if (best == null) {
                best = ref;
                continue;
            }
            boolean refMoreSpecific = ctx.types.isAssignable(ref.target, best.target);
            boolean bestMoreSpecific = ctx.types.isAssignable(best.target, ref.target);
            if (refMoreSpecific && !bestMoreSpecific) best = ref;
            else if ((!refMoreSpecific && !bestMoreSpecific) || (refMoreSpecific && bestMoreSpecific)) {
                fail(method, "Ambiguous @MappingCreator for target type " + requested);
                return null;
            }
        }
        return best;
    }

    private Match factory(ExecutableElement method, Ref ref, TypeMirror requested) {
        String creator = ref.creator;
        if (!creator.startsWith("this::") || !simple(creator.substring(6))) {
            fail(method, "@MappingCreator.creator currently supports only this::method");
            return null;
        }
        String name = creator.substring(6);
        ExecutableElement found = findFactory(method, ref.owner, name);
        if (found == null) return null;
        if (!found.getModifiers().contains(Modifier.DEFAULT) && !found.getModifiers().contains(Modifier.STATIC)) {
            fail(method, "@MappingCreator creator method must be default or static");
            return null;
        }
        if (!found.getParameters().isEmpty()) {
            fail(method, "@MappingCreator creator method must not declare parameters");
            return null;
        }
        TypeElement owner = (TypeElement) found.getEnclosingElement();
        TypeMirror result = ((ExecutableType) ctx.types.asMemberOf((DeclaredType) owner.asType(), found)).getReturnType();
        if (!ctx.types.isAssignable(result, ref.target)) {
            fail(method, "@MappingCreator creator method return type must be assignable to " + requested);
            return null;
        }
        String call = found.getModifiers().contains(Modifier.STATIC)
                ? owner.getQualifiedName() + "." + name + "()" : name + "()";
        return new Match(result, call);
    }

    /**
     * Default factories follow the generated mapper interface, so child
     * overrides remain visible. Static interface methods are not inherited,
     * therefore an inherited creator may fall back to its declaring owner.
     */
    private ExecutableElement findFactory(ExecutableElement method, TypeElement creatorOwner, String name) {
        ExecutableElement found = findNamed(method, ctx.elements.getAllMembers(iface), name);
        if (found != null) return found;
        for (Element element : creatorOwner.getEnclosedElements()) {
            if (element.getKind() == ElementKind.METHOD && element.getSimpleName().contentEquals(name)
                    && element.getModifiers().contains(Modifier.STATIC)) return (ExecutableElement) element;
        }
        fail(method, "Cannot resolve @MappingCreator creator method 'this::" + name + "'");
        return null;
    }

    private ExecutableElement findNamed(ExecutableElement method, List<? extends Element> candidates, String name) {
        ExecutableElement found = null;
        for (Element element : candidates) {
            if (element.getKind() != ElementKind.METHOD || !element.getSimpleName().contentEquals(name)
                    || element.getEnclosingElement().toString().equals(Object.class.getName())) continue;
            if (found != null) {
                fail(method, "Ambiguous @MappingCreator creator method '" + name + "'");
                return null;
            }
            found = (ExecutableElement) element;
        }
        return found;
    }

    private void fail(Element element, String message) {
        failed = true;
        errors.error(element, message);
    }

    private static String qualified(TypeMirror type) {
        TypeElement element = GeneratorUtil.asTypeElement(type);
        return element == null ? "" : element.getQualifiedName().toString();
    }

    private static boolean simple(String value) {
        if (value.isEmpty() || !Character.isJavaIdentifierStart(value.charAt(0))) return false;
        for (int i = 1; i < value.length(); i++) if (!Character.isJavaIdentifierPart(value.charAt(i))) return false;
        return true;
    }
}
