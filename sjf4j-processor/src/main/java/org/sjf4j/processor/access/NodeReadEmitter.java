package org.sjf4j.processor.access;

import org.sjf4j.processor.property.PropertyAccess;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.type.TypeMirror;

/**
 * Shared expressions for statically resolved OBNT reads.
 *
 * <p>Only emits a value expression. Callers own presence checks, index
 * normalization, bounds checks, and missing/null semantics. The returned
 * expressions are evaluated directly in generated Java code.</p>
 */
public final class NodeReadEmitter {
    private final TypeSystem types;

    public NodeReadEmitter(TypeSystem types) {
        this.types = types;
    }

    /** @param key already escaped Java string expression, or a String parameter */
    public String name(NodeAccess access, String owner, String key) {
        switch (access.kind()) {
            case PROPERTY: {
                PropertyAccess read = access.property().read();
                return owner + "." + read.memberName() + (read.isMethod() ? "()" : "");
            }
            case MAP:
                return owner + ".get(" + key + ")";
            case JSON_OBJECT:
                return cast(access.readType(), owner + ".getNode(" + key + ")");
            case DYNAMIC:
                return cast(access.readType(),
                        "org.sjf4j.node.NodeWalker.getInObject(" + owner + ", " + key + ")");
            default:
                throw new IllegalArgumentException("Not a named node access: " + access.kind());
        }
    }

    /** @param index a normalized index expression; caller owns bounds checks */
    public String index(NodeAccess access, String owner, String index) {
        switch (access.kind()) {
            case ARRAY:
                return owner + "[" + index + "]";
            case LIST:
                return owner + ".get(" + index + ")";
            case JSON_ARRAY:
                return cast(access.readType(), owner + ".getNode(" + index + ")");
            case DYNAMIC:
                return cast(access.readType(),
                        "org.sjf4j.node.NodeWalker.getInArray(" + owner + ", " + index + ")");
            default:
                throw new IllegalArgumentException("Not an indexed node access: " + access.kind());
        }
    }

    private String cast(TypeMirror type, String expression) {
        if (types.isObject(type)) return expression;
        return "(" + types.boxed(types.concrete(type)) + ") " + expression;
    }
}
