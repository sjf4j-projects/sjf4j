package org.sjf4j.facades;

import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.facades.fastjson2.Fastjson2Writer;
import org.sjf4j.facades.jackson.JacksonReader;
import org.sjf4j.util.StreamingUtil;
import org.sjf4j.util.TypeReference;
import org.sjf4j.util.TypeUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;

public interface StreamingFacade<R extends FacadeReader, W extends FacadeWriter> {

    R createReader(Reader input) throws IOException;
    W createWriter(Writer output) throws IOException;

    default Object readNode(@NonNull Reader input, Type type) throws IOException {
        try {
            FacadeReader reader = createReader(input);
            reader.startDocument();
            Object node = StreamingUtil.readNode(reader, type);
            reader.endDocument();
            return node;
        } catch (IOException e) {
            throw new JsonException("Failed to read streaming into node of type '" + type + "'", e);
        }
    }

    void writeNode(Writer output, Object node) throws IOException;

}
