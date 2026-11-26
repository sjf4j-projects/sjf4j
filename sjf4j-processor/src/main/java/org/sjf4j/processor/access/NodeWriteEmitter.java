package org.sjf4j.processor.access;

import org.sjf4j.processor.property.PropertyAccess;

/**
 * Shared Java write expressions for statically resolved OBNT accesses.
 *
 * <p>Returns a statement expression without a semicolon. Callers own null,
 * presence and bounds checks, index normalization, append decisions, and
 * whether a returned previous value is captured. No runtime abstraction is
 * introduced into the generated source.</p>
 */
public final class NodeWriteEmitter {

    private NodeWriteEmitter() {
    }

    /** The key is an already escaped Java expression; properties ignore it. */
    public static String name(NodeAccess access, String owner, String key, String value) {
        switch (access.kind()) {
            case PROPERTY: {
                PropertyAccess write = access.property().write();
                return write.isMethod()
                        ? owner + "." + write.memberName() + "(" + value + ")"
                        : owner + "." + write.memberName() + " = " + value;
            }
            case MAP:
            case JSON_OBJECT:
                return owner + ".put(" + key + ", " + value + ")";
            case DYNAMIC:
                return "org.sjf4j.node.NodeWalker.putInObject(" + owner + ", " + key + ", " + value + ")";
            default:
                throw new IllegalArgumentException("Not a named node write: " + access.kind());
        }
    }

    /** The index is already normalized as required by the caller. */
    public static String index(NodeAccess access, String owner, String index, String value) {
        switch (access.kind()) {
            case ARRAY:
                return owner + "[" + index + "] = " + value;
            case LIST:
            case JSON_ARRAY:
                return owner + ".set(" + index + ", " + value + ")";
            case DYNAMIC:
                return "org.sjf4j.node.NodeWalker.putInArray(" + owner + ", " + index + ", " + value + ")";
            default:
                throw new IllegalArgumentException("Not an indexed node write: " + access.kind());
        }
    }

    public static String append(NodeAccess access, String owner, String value) {
        switch (access.kind()) {
            case LIST:
            case JSON_ARRAY:
                return owner + ".add(" + value + ")";
            case DYNAMIC:
                return "org.sjf4j.node.NodeWalker.addInArray(" + owner + ", " + value + ")";
            default:
                throw new IllegalArgumentException("Not an appendable node: " + access.kind());
        }
    }
}
