package org.sjf4j.facades;

import lombok.NonNull;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.facades.fastjson2.Fastjson2Writer;
import org.sjf4j.util.StreamingUtil;
import org.sjf4j.util.TypeReference;
import org.sjf4j.util.TypeUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Type;

public interface StreamingFacade<R extends FacadeReader, W extends FacadeWriter> {

    R createReader(Reader input) throws IOException;
    W createWriter(Writer output) throws IOException;

    default Object readNode(@NonNull Reader input, Type type) {
        // Always use try-with-resources here.
        // It enables JVM optimizations (escape analysis, inlining) that significantly improve performance.
        try (FacadeReader reader = createReader(input)) {
            reader.startDocument();
            Object node = StreamingUtil.readNode(reader, type);
            reader.endDocument();
            return node;
        } catch (IOException e) {
            throw new JsonException("Failed to read streaming into node of type '" + type + "'", e);
        }
    }

    default void writeNode(@NonNull Writer output, Object node) {
        try (FacadeWriter writer = createWriter(output)) {
            writer.startDocument();
            StreamingUtil.writeNode(writer, node);
            writer.endDocument();

            if (writer instanceof Fastjson2Writer) {
                ((Fastjson2Writer) writer).flushTo(output);
            }
        } catch (IOException e) {
            throw new JsonException("Failed to write node of type '" + TypeUtil.typeName(node) + "' to streaming", e);
        }
    }


    @SuppressWarnings("unchecked")
    default <T> T readObject(@NonNull Reader input, @NonNull Class<T> clazz) {
        try {
            return (T) readNode(input, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to read streaming into node of type '" + clazz + "'", e);
        }
    }

    @SuppressWarnings("unchecked")
    default <T> T readObject(@NonNull Reader input, @NonNull TypeReference<T> type) {
        try {
            return (T) readNode(input, type.getType());
        } catch (Exception e) {
            throw new JsonException("Failed to read streaming into node of type '" + type + "'", e);
        }
    }

    default JsonObject readObject(@NonNull Reader input) {
        try {
            return readObject(input, JsonObject.class);
        } catch (Exception e) {
            throw new JsonException("Failed to read streaming into node of type 'JsonObject'", e);
        }
    }

    default <T> T readObject(@NonNull String input, @NonNull Class<T> clazz) {
        return readObject(new StringReader(input), clazz);
    }

    default <T> T readObject(@NonNull String input, @NonNull TypeReference<T> type) {
        return readObject(new StringReader(input), type);
    }

    default JsonObject readObject(@NonNull String input) {
        return readObject(new StringReader(input), JsonObject.class);
    }

    default String writeNode(Object node) {
        StringWriter output = new StringWriter();
        writeNode(output, node);
        return output.toString();
    }

}
