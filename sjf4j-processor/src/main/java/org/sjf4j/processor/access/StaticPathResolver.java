package org.sjf4j.processor.access;

import org.sjf4j.path.PathSegment;

import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves linear paths against Java types. Feature compilers own syntax
 * restrictions, read/write requirements and diagnostic wording.
 */
public final class StaticPathResolver {

    private final NodeAccessResolver access;

    public StaticPathResolver(NodeAccessResolver access) {
        this.access = access;
    }

    /** Checks feature-specific syntax restrictions before resolving access. */
    @FunctionalInterface
    public interface SegmentPolicy {
        boolean accepts(int index, boolean last, PathSegment segment);
    }

    /** Validates a step and selects the type used for the next step; null rejects it. */
    @FunctionalInterface
    public interface StepPolicy {
        TypeMirror resultType(int index, boolean last, ResolvedStep step);
    }

    public ResolvedStep resolveStep(TypeMirror owner, PathSegment segment) {
        NodeAccess resolved = null;
        if (segment instanceof PathSegment.Name) {
            resolved = access.resolveName(owner, ((PathSegment.Name) segment).name);
        } else if (segment instanceof PathSegment.Index) {
            resolved = access.resolveIndex(owner);
        } else if (segment instanceof PathSegment.Append) {
            resolved = access.resolveAppend(owner);
        }
        return new ResolvedStep(owner, segment, resolved);
    }

    /** Resolves a parameter whose name/index shape has been checked by the caller. */
    public ResolvedStep resolveParameter(TypeMirror owner, PathSegment.Param segment, boolean named) {
        return new ResolvedStep(owner, segment,
                named ? access.resolveDynamicName(owner) : access.resolveIndex(owner));
    }

    /** Walks a root-prefixed path, stopping at the first rejected step. */
    public ResolvedPath resolve(TypeMirror rootType, PathSegment[] segments,
                                SegmentPolicy segmentsPolicy, StepPolicy stepPolicy) {
        TypeMirror current = rootType;
        List<ResolvedStep> steps = new ArrayList<>(Math.max(0, segments.length - 1));
        for (int i = 1; i < segments.length; i++) {
            PathSegment segment = segments[i];
            boolean last = i == segments.length - 1;
            if (!segmentsPolicy.accepts(i, last, segment)) return null;
            ResolvedStep step = resolveStep(current, segment);
            current = stepPolicy.resultType(i, last, step);
            if (current == null) return null;
            steps.add(step);
        }
        return new ResolvedPath(rootType, steps, current);
    }
}
