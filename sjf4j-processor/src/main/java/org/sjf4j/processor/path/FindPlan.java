package org.sjf4j.processor.path;

import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.access.NodeAccess;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.Collections;
import java.util.List;

/** Resolved, immutable compile-time traversal; no runtime plan is generated. */
final class FindPlan {

    enum Kind {
        COLLECT, READ, WILDCARD, SLICE, FILTER, UNION, DESCENDANT, EMPTY
    }

    final ExecutableElement method;
    final VariableElement root;
    final String expression;
    final TypeMirror elementType;
    final Step traversal;

    FindPlan(ExecutableElement method, VariableElement root, String expression,
             TypeMirror elementType, Step traversal) {
        this.method = method;
        this.root = root;
        this.expression = expression;
        this.elementType = elementType;
        this.traversal = traversal;
    }

    static final class Step {
        final Kind kind;
        final PathSegment segment;
        final int segmentIndex;
        final TypeMirror ownerType;
        final TypeMirror valueType;
        final NodeAccess access;
        final Step next;
        final List<Step> alternatives;

        Step(Kind kind, PathSegment segment, int segmentIndex, TypeMirror ownerType,
             TypeMirror valueType, NodeAccess access, Step next, List<Step> alternatives) {
            this.kind = kind;
            this.segment = segment;
            this.segmentIndex = segmentIndex;
            this.ownerType = ownerType;
            this.valueType = valueType;
            this.access = access;
            this.next = next;
            this.alternatives = alternatives == null ? Collections.<Step>emptyList()
                    : Collections.unmodifiableList(alternatives);
        }

        static Step collect(TypeMirror ownerType, TypeMirror resultType) {
            return new Step(Kind.COLLECT, null, -1, ownerType, resultType, null, null, null);
        }

        static Step empty(TypeMirror type) {
            return new Step(Kind.EMPTY, null, -1, type, type, null, null, null);
        }
    }
}
