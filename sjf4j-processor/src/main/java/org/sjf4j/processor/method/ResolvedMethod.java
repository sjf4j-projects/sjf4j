package org.sjf4j.processor.method;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ExecutableType;
import java.util.Objects;

/**
 * One effective interface method together with its type specialized through
 * the owning interface.
 */
public final class ResolvedMethod {

    private final ExecutableElement declaration;
    private final ExecutableType type;


    ResolvedMethod(
            ExecutableElement declaration,
            ExecutableType type) {

        this.declaration =
                Objects.requireNonNull(
                        declaration,
                        "declaration");

        this.type =
                Objects.requireNonNull(
                        type,
                        "type");
    }


    public ExecutableElement declaration() {
        return declaration;
    }

    public ExecutableType type() {
        return type;
    }
}
