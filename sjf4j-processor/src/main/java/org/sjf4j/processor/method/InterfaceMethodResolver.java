package org.sjf4j.processor.method;

import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves the effective abstract instance methods visible from an interface.
 *
 * <p>Inheritance, generic specialization, overriding and covariant returns are
 * normalized here so feature generators do not maintain their own Java method
 * model.</p>
 */
public final class InterfaceMethodResolver {

    private final Types typeUtils;
    private final Elements elements;
    private final TypeSystem types;


    public InterfaceMethodResolver(
            Types typeUtils,
            Elements elements,
            TypeSystem types) {

        this.typeUtils =
                Objects.requireNonNull(
                        typeUtils,
                        "typeUtils");

        this.elements =
                Objects.requireNonNull(
                        elements,
                        "elements");

        this.types =
                Objects.requireNonNull(
                        types,
                        "types");
    }


    /**
     * Returns effective abstract instance methods specialized through owner.
     */
    public List<ResolvedMethod> abstractMethods(
            TypeElement owner) {

        Map<String, ResolvedMethod> methods =
                new LinkedHashMap<String, ResolvedMethod>();

        for (Element member :
                elements.getAllMembers(owner)) {

            if (member.getKind() !=
                    ElementKind.METHOD) {

                continue;
            }

            ExecutableElement method =
                    (ExecutableElement) member;

            if (!method.getModifiers()
                    .contains(Modifier.ABSTRACT)) {

                continue;
            }

            if (method.getModifiers()
                    .contains(Modifier.STATIC) ||
                    method.getModifiers()
                            .contains(Modifier.PRIVATE)) {

                continue;
            }

            ExecutableType resolved =
                    types.resolveMethodType(
                            owner.asType(),
                            method);

            if (resolved == null) {
                /*
                 * This should not normally happen for a member returned by
                 * getAllMembers(). Preserve the declaration type so feature
                 * validation can still produce a useful diagnostic.
                 */
                resolved =
                        (ExecutableType)
                                method.asType();
            }

            ResolvedMethod candidate =
                    new ResolvedMethod(
                            method,
                            resolved);

            String key =
                    signatureKey(candidate);

            ResolvedMethod current =
                    methods.get(key);

            if (current == null) {
                methods.put(
                        key,
                        candidate);
            } else {
                methods.put(
                        key,
                        moreSpecific(
                                owner,
                                current,
                                candidate));
            }
        }

        List<ResolvedMethod> result =
                new ArrayList<ResolvedMethod>(
                        methods.values());

        Collections.sort(
                result,
                new Comparator<ResolvedMethod>() {
                    @Override
                    public int compare(
                            ResolvedMethod first,
                            ResolvedMethod second) {

                        int compared =
                                signatureKey(first)
                                        .compareTo(
                                                signatureKey(second));

                        if (compared != 0) {
                            return compared;
                        }

                        return declarationKey(first)
                                .compareTo(
                                        declarationKey(second));
                    }
                });

        return result;
    }


    private ResolvedMethod moreSpecific(
            TypeElement owner,
            ResolvedMethod first,
            ResolvedMethod second) {

        ExecutableElement firstMethod =
                first.declaration();

        ExecutableElement secondMethod =
                second.declaration();

        try {
            if (elements.overrides(
                    secondMethod,
                    firstMethod,
                    owner)) {

                return second;
            }

            if (elements.overrides(
                    firstMethod,
                    secondMethod,
                    owner)) {

                return first;
            }
        } catch (IllegalArgumentException ignored) {
            /*
             * Some compiler implementations are conservative for equivalent
             * inherited interface members. Resolved return specificity below
             * provides the deterministic fallback we need.
             */
        }

        TypeMirror firstReturn =
                first.type()
                        .getReturnType();

        TypeMirror secondReturn =
                second.type()
                        .getReturnType();

        if (firstReturn.getKind().isPrimitive() ||
                secondReturn.getKind().isPrimitive() ||
                firstReturn.getKind() == TypeKind.VOID ||
                secondReturn.getKind() == TypeKind.VOID) {

            return declarationKey(second)
                    .compareTo(
                            declarationKey(first)) < 0
                    ? second
                    : first;
        }

        boolean secondToFirst =
                typeUtils.isAssignable(
                        secondReturn,
                        firstReturn);

        boolean firstToSecond =
                typeUtils.isAssignable(
                        firstReturn,
                        secondReturn);

        if (secondToFirst &&
                !firstToSecond) {

            return second;
        }

        if (firstToSecond &&
                !secondToFirst) {

            return first;
        }

        /*
         * Equivalent inherited declarations. The Java compiler reports any
         * genuinely incompatible inheritance; use a stable declaration key
         * only to remove getAllMembers() ordering from generated output.
         */
        return declarationKey(second)
                .compareTo(
                        declarationKey(first)) < 0
                ? second
                : first;
    }


    private String signatureKey(
            ResolvedMethod method) {

        StringBuilder key =
                new StringBuilder();

        key.append(
                method.declaration()
                        .getSimpleName());

        key.append('(');

        List<? extends TypeMirror> parameters =
                method.type()
                        .getParameterTypes();

        for (int i = 0;
             i < parameters.size();
             i++) {

            if (i != 0) {
                key.append(',');
            }

            key.append(
                    typeUtils.erasure(
                            parameters.get(i)));
        }

        key.append(')');

        return key.toString();
    }


    private String declarationKey(
            ResolvedMethod method) {

        ExecutableElement declaration =
                method.declaration();

        Element enclosing =
                declaration.getEnclosingElement();

        String owner =
                enclosing instanceof TypeElement
                        ? ((TypeElement) enclosing)
                        .getQualifiedName()
                        .toString()
                        : enclosing.toString();

        return owner +
                '#' +
                declaration.toString();
    }
}
