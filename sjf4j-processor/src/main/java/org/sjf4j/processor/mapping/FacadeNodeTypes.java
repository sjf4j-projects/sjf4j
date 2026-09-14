package org.sjf4j.processor.mapping;

import org.sjf4j.facade.gson.GsonNodes;
import org.sjf4j.facade.jackson2.Jackson2Nodes;
import org.sjf4j.facade.jackson3.Jackson3Nodes;
import org.sjf4j.processor.ProcessorContext;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/** Compile-time classification for the optional facade node families. */
final class FacadeNodeTypes {
    private final ProcessorContext ctx;
    private final TypeMirror jackson2;
    private final TypeMirror jackson3;
    private final TypeMirror gson;

    FacadeNodeTypes(ProcessorContext ctx) {
        this.ctx = ctx;
        jackson2 = _type(Jackson2Nodes.ROOT_NODE_TYPE_NAME);
        jackson3 = _type(Jackson3Nodes.ROOT_NODE_TYPE_NAME);
        gson = _type(GsonNodes.ROOT_NODE_TYPE_NAME);
    }

    boolean isFacadeNode(TypeMirror type) {
        return _is(type, jackson2) || _is(type, jackson3) || _is(type, gson);
    }

    private TypeMirror _type(String name) {
        TypeElement type = ctx.elements.getTypeElement(name);
        return type == null ? null : type.asType();
    }

    private boolean _is(TypeMirror type, TypeMirror root) {
        return root != null && ctx.types.isAssignable(type, root);
    }
}
