package org.sjf4j.processor.access;

import org.sjf4j.path.PathSegment;

import javax.lang.model.type.TypeMirror;

/** One path segment and its statically resolved Java access. */
public final class ResolvedStep {

    private final TypeMirror ownerType;
    private final PathSegment segment;
    private final NodeAccess access;

    /** Access may be null while a caller is diagnosing an unsupported step. */
    public ResolvedStep(TypeMirror ownerType, PathSegment segment, NodeAccess access) {
        this.ownerType = ownerType;
        this.segment = segment;
        this.access = access;
    }

    public TypeMirror ownerType() {
        return ownerType;
    }

    public PathSegment segment() {
        return segment;
    }

    public NodeAccess access() {
        return access;
    }
}
