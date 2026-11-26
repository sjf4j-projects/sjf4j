package org.sjf4j.processor.mapping.target;

import org.sjf4j.processor.mapping.CreatorResolver;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;

/** Constructor and factory expressions; callers retain argument evaluation and cast policies. */
public final class TargetCreationEmitter {

    private TargetCreationEmitter() {
    }

    public static String constructor(String type, Iterable<String> arguments) {
        return "new " + type + "(" + String.join(", ", arguments) + ")";
    }

    public static String factory(CreatorResolver.Creation creation, boolean explicitThis) {
        ExecutableElement factory = creation.factory();
        String receiver;
        if (creation.factoryOwner() == null ||
                (explicitThis && !factory.getModifiers().contains(Modifier.STATIC))) {
            receiver = explicitThis ? "this." : "";
        } else {
            receiver = creation.factoryOwner().getQualifiedName() + ".";
        }
        return receiver + factory.getSimpleName() + "()";
    }
}
