package org.sjf4j.processor.creator;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Compile-time Java object construction metadata, independent of the source
 * supplying constructor arguments (JSON, Java objects, or JDBC columns).
 */
public final class CreatorPlan {

    public enum Kind {
        NO_ARGS,
        CONSTRUCTOR,
        STATIC_FACTORY
    }

    private final Kind kind;
    private final TypeMirror declaredType;
    private final TypeMirror targetType;
    private final ExecutableElement executable;
    private final List<? extends TypeMirror> parameterTypes;

    private CreatorPlan(Kind kind, TypeMirror declaredType, TypeMirror targetType,
                        ExecutableElement executable, List<? extends TypeMirror> parameterTypes) {
        this.kind = kind;
        this.declaredType = declaredType;
        this.targetType = targetType;
        this.executable = executable;
        this.parameterTypes = parameterTypes == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<TypeMirror>(parameterTypes));
    }

    public static CreatorPlan noArgs(TypeMirror declaredType, TypeMirror targetType) {
        return new CreatorPlan(Kind.NO_ARGS, declaredType, targetType, null, null);
    }

    public static CreatorPlan constructor(TypeMirror declaredType, TypeMirror targetType,
                                          ExecutableElement constructor, List<? extends TypeMirror> parameterTypes) {
        return new CreatorPlan(Kind.CONSTRUCTOR, declaredType, targetType, constructor, parameterTypes);
    }

    public static CreatorPlan staticFactory(TypeMirror declaredType, TypeMirror targetType,
                                            ExecutableElement factory, List<? extends TypeMirror> parameterTypes) {
        return new CreatorPlan(Kind.STATIC_FACTORY, declaredType, targetType, factory, parameterTypes);
    }

    public Kind kind() {
        return kind;
    }

    public TypeMirror declaredType() {
        return declaredType;
    }

    public TypeMirror targetType() {
        return targetType;
    }

    public ExecutableElement executable() {
        return executable;
    }

    public List<? extends TypeMirror> parameterTypes() {
        return parameterTypes;
    }
}
