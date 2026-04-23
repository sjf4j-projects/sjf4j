package org.sjf4j.facade.jackson2;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import org.sjf4j.facade.StreamingContext;
import org.sjf4j.facade.StreamingIO;
import org.sjf4j.node.NodeRegistry;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Jackson2 streaming entry points aligned with {@link StreamingIO} semantics.
 */
public class Jackson2StreamingIO {

    public static Object readNode(JsonParser parser, Type type) throws IOException {
        return readNode(parser, type, StreamingContext.EMPTY);
    }

    public static Object readNode(JsonParser parser, Type type, StreamingContext context) throws IOException {
        Objects.requireNonNull(parser, "parser");
        return StreamingIO.readNode(new Jackson2Reader(parser), type,
                context == null ? StreamingContext.EMPTY : context);
    }

    public static Object readPojo(JsonParser parser, Type ownerType, Class<?> ownerRawClazz,
                                  NodeRegistry.PojoInfo pi) throws IOException {
        return readPojo(parser, ownerType, ownerRawClazz, pi, StreamingContext.EMPTY);
    }

    public static Object readPojo(JsonParser parser, Type ownerType, Class<?> ownerRawClazz,
                                  NodeRegistry.PojoInfo pi, StreamingContext context) throws IOException {
        Objects.requireNonNull(parser, "parser");
        return StreamingIO.readPojo(new Jackson2Reader(parser), ownerType, ownerRawClazz, pi,
                context == null ? StreamingContext.EMPTY : context);
    }

    public static Object readAnyOf(JsonParser parser, NodeRegistry.AnyOfInfo anyOfInfo) throws IOException {
        return readAnyOf(parser, anyOfInfo, StreamingContext.EMPTY);
    }

    public static Object readAnyOf(JsonParser parser, NodeRegistry.AnyOfInfo anyOfInfo,
                                   StreamingContext context) throws IOException {
        Objects.requireNonNull(parser, "parser");
        return StreamingIO.readAnyOf(new Jackson2Reader(parser), anyOfInfo,
                context == null ? StreamingContext.EMPTY : context);
    }

    public static void writeNode(JsonGenerator gen, Object node) throws IOException {
        writeNode(gen, node, StreamingContext.EMPTY);
    }

    public static void writeNode(JsonGenerator gen, Object node, StreamingContext context) throws IOException {
        Objects.requireNonNull(gen, "gen");
        StreamingIO.writeNode(new Jackson2Writer(gen), node, context == null ? StreamingContext.EMPTY : context);
    }
}
