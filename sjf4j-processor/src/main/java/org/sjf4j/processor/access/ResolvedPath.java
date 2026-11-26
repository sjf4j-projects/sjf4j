package org.sjf4j.processor.access;

import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, successfully resolved path with its root and final Java types. */
public final class ResolvedPath {

    private final TypeMirror rootType;
    private final List<ResolvedStep> steps;
    private final TypeMirror resultType;

    public ResolvedPath(TypeMirror rootType, List<ResolvedStep> steps, TypeMirror resultType) {
        this.rootType = rootType;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        this.resultType = resultType;
    }

    public TypeMirror rootType() {
        return rootType;
    }

    public List<ResolvedStep> steps() {
        return steps;
    }

    public TypeMirror resultType() {
        return resultType;
    }
}
