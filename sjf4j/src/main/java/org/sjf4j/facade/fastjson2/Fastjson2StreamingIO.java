package org.sjf4j.facade.fastjson2;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.sjf4j.facade.StreamingContext;
import org.sjf4j.facade.StreamingIO;
import org.sjf4j.node.NodeRegistry;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Fastjson2 streaming entry points aligned with {@link StreamingIO} semantics.
 */
public class Fastjson2StreamingIO {

    public static Object readNode(JSONReader reader, Type type) {
        return readNode(reader, type, StreamingContext.EMPTY);
    }

    public static Object readNode(JSONReader reader, Type type, StreamingContext context) {
        Objects.requireNonNull(reader, "reader");
        return StreamingIO.readNode(new Fastjson2Reader(reader), type,
                context == null ? StreamingContext.EMPTY : context);
    }

    public static Object readPojo(JSONReader reader, Type ownerType, Class<?> ownerRawClazz,
                                  NodeRegistry.PojoInfo pi) throws IOException {
        return readPojo(reader, ownerType, ownerRawClazz, pi, StreamingContext.EMPTY);
    }

    public static Object readPojo(JSONReader reader, Type ownerType, Class<?> ownerRawClazz,
                                  NodeRegistry.PojoInfo pi, StreamingContext context) throws IOException {
        Objects.requireNonNull(reader, "reader");
        return StreamingIO.readPojo(new Fastjson2Reader(reader), ownerType, ownerRawClazz, pi,
                context == null ? StreamingContext.EMPTY : context);
    }

    public static Object readAnyOf(JSONReader reader, NodeRegistry.AnyOfInfo anyOfInfo) throws IOException {
        return readAnyOf(reader, anyOfInfo, StreamingContext.EMPTY);
    }

    public static Object readAnyOf(JSONReader reader, NodeRegistry.AnyOfInfo anyOfInfo,
                                   StreamingContext context) throws IOException {
        Objects.requireNonNull(reader, "reader");
        return StreamingIO.readAnyOf(new Fastjson2Reader(reader), anyOfInfo,
                context == null ? StreamingContext.EMPTY : context);
    }

    public static void writeNode(JSONWriter writer, Object node) throws IOException {
        writeNode(writer, node, StreamingContext.EMPTY);
    }

    public static void writeNode(JSONWriter writer, Object node, StreamingContext context) throws IOException {
        Objects.requireNonNull(writer, "writer");
        StreamingIO.writeNode(new Fastjson2Writer(writer), node, context == null ? StreamingContext.EMPTY : context);
    }
}
