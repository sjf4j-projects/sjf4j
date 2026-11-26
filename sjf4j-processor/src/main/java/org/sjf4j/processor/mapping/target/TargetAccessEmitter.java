package org.sjf4j.processor.mapping.target;

import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.access.NodeReadEmitter;
import org.sjf4j.processor.access.NodeWriteEmitter;
import org.sjf4j.processor.code.JavaExpressions;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.type.TypeMirror;

/** Emits target access; callers own traversal, null guards and index normalization. */
public final class TargetAccessEmitter {

    /** JDBC keeps explicit casts on typed map/list reads. */
    public enum ReadPolicy { DIRECT, CAST_CONTAINER }

    /** Mapping may grow indexed containers; JDBC writes existing positions only. */
    public enum IndexPolicy { SET_OR_APPEND, SET_ONLY }

    private final TypeSystem types;
    private final NodeReadEmitter reads;

    public TargetAccessEmitter(TypeSystem types) {
        this.types = types;
        this.reads = new NodeReadEmitter(types);
    }

    public String read(String owner, PathSegment segment, String index,
                       NodeAccess access, ReadPolicy policy) {
        if (segment instanceof PathSegment.Name) {
            String key = JavaWriter.stringLiteral(((PathSegment.Name) segment).name);
            if (policy == ReadPolicy.CAST_CONTAINER &&
                    (access.kind() == NodeAccess.Kind.MAP || access.kind() == NodeAccess.Kind.JSON_OBJECT)) {
                return cast(access.readType(), NodeReadEmitter.rawName(access, owner, key));
            }
            return reads.name(access, owner, key);
        }
        if (segment instanceof PathSegment.Index) {
            String resolved = index != null ? index : Integer.toString(((PathSegment.Index) segment).index);
            if (policy == ReadPolicy.CAST_CONTAINER &&
                    (access.kind() == NodeAccess.Kind.LIST || access.kind() == NodeAccess.Kind.JSON_ARRAY)) {
                return cast(access.readType(), NodeReadEmitter.rawIndex(access, owner, resolved));
            }
            return reads.index(access, owner, resolved);
        }
        throw new IllegalStateException("unsupported target read: " + segment);
    }

    public void write(JavaWriter out, String owner, PathSegment segment, String index,
                      NodeAccess access, String value, IndexPolicy policy) {
        if (segment instanceof PathSegment.Name) {
            String key = JavaWriter.stringLiteral(((PathSegment.Name) segment).name);
            out.line(NodeWriteEmitter.name(access, owner, key, value) + ";");
            return;
        }
        if (segment instanceof PathSegment.Append) {
            if (policy == IndexPolicy.SET_ONLY) throw new IllegalStateException("append is not supported");
            out.line(NodeWriteEmitter.append(access, owner, value) + ";");
            return;
        }
        if (segment instanceof PathSegment.Index) {
            String actualIndex = index != null ? index : Integer.toString(((PathSegment.Index) segment).index);
            if (policy == IndexPolicy.SET_OR_APPEND &&
                    (access.kind() == NodeAccess.Kind.LIST || access.kind() == NodeAccess.Kind.JSON_ARRAY)) {
                out.beginBlock("if (" + actualIndex + " == " + owner + ".size())");
                out.line(NodeWriteEmitter.append(access, owner, value) + ";");
                out.endBlock();
                out.beginBlock("else");
                out.line(NodeWriteEmitter.index(access, owner, actualIndex, value) + ";");
                out.endBlock();
            } else {
                out.line(NodeWriteEmitter.index(access, owner, actualIndex, value) + ";");
            }
            return;
        }
        throw new IllegalStateException("unsupported target segment: " + segment);
    }

    private String cast(TypeMirror type, String expression) {
        if (type == null || types.isObject(type)) return expression;
        return JavaExpressions.cast(JavaExpressions.boxedType(types, type), expression);
    }
}
