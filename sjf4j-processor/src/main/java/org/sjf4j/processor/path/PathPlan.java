package org.sjf4j.processor.path;

import org.sjf4j.processor.access.ResolvedStep;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fully resolved Get/Put path plans, independent of Java source emission. */
abstract class PathPlan {

    private PathPlan() {
    }

    static final class Get extends PathPlan {
        final NavigatorPlan.Get plan;
        final List<Step> steps;

        Get(NavigatorPlan.Get plan, List<Step> steps) {
            this.plan = plan;
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        }
    }

    static final class Put extends PathPlan {
        final NavigatorPlan.Put plan;
        final List<Step> steps;
        final Step target;

        Put(NavigatorPlan.Put plan, List<Step> steps, Step target) {
            this.plan = plan;
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
            this.target = target;
        }
    }

    /** A resolved access plus Navigator-specific parameter and Ensure semantics. */
    static final class Step {
        final ResolvedStep resolved;
        final VariableElement parameter;
        final TypeMirror resultType;
        final TypeMirror oldType;
        final Creation creation;

        Step(ResolvedStep resolved, VariableElement parameter, TypeMirror resultType,
             TypeMirror oldType, Creation creation) {
            this.resolved = resolved;
            this.parameter = parameter;
            this.resultType = resultType;
            this.oldType = oldType;
            this.creation = creation;
        }

        Step withCreation(Creation creation) {
            return new Step(resolved, parameter, resultType, oldType, creation);
        }
    }

    /** A validated no-arg constructor and whether its Java type needs a diamond. */
    static final class Creation {
        final String typeName;
        final boolean diamond;

        Creation(String typeName, boolean diamond) {
            this.typeName = typeName;
            this.diamond = diamond;
        }
    }
}
