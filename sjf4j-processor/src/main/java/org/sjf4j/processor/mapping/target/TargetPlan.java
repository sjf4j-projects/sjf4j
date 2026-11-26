package org.sjf4j.processor.mapping.target;

import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.access.ResolvedPath;
import org.sjf4j.processor.access.ResolvedStep;

import javax.lang.model.type.TypeMirror;
import java.util.Collections;
import java.util.List;

/** A writable property or resolved target path, independent of the source of its value. */
public final class TargetPlan {

    public enum Kind { PROPERTY, PATH }

    public enum WriteMode { STRICT, IF_PARENT_PRESENT, ENSURE }

    private final Kind kind;
    private final String expression;
    private final WriteMode mode;
    private final ResolvedPath path;

    private TargetPlan(Kind kind, String expression, WriteMode mode, ResolvedPath path) {
        this.kind = kind;
        this.expression = expression;
        this.mode = mode;
        this.path = path;
    }

    public static TargetPlan property(String name, NodeAccess access) {
        ResolvedStep step = new ResolvedStep(access.ownerType(),
                new PathSegment.Name(PathSegment.Root.INSTANCE, name), access);
        return new TargetPlan(Kind.PROPERTY, name, WriteMode.STRICT,
                new ResolvedPath(access.ownerType(), Collections.singletonList(step), access.writeType()));
    }

    public static TargetPlan path(String expression, WriteMode mode, ResolvedPath path) {
        return new TargetPlan(Kind.PATH, expression, mode, path);
    }

    public Kind kind() { return kind; }

    public String expression() { return expression; }

    public WriteMode mode() { return mode; }

    public TypeMirror type() { return path.resultType(); }

    public List<ResolvedStep> steps() { return path.steps(); }

    /** Only direct properties have an access relative to the root. */
    public NodeAccess propertyAccess() {
        return kind == Kind.PROPERTY ? path.steps().get(0).access() : null;
    }
}
