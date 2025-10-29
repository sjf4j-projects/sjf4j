package org.sjf4j.facades.snake;

import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.util.ValueHandler;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.emitter.Emitter;
import org.yaml.snakeyaml.events.ImplicitTuple;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

import java.io.IOException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public class SimpleSnakeEmitter {


    public static void write(@NonNull Emitter emitter, Object object) throws IOException {
        Object value = ValueHandler.object2Value(object);
        if (value == null) {
            emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                    "null", null, null, DumperOptions.ScalarStyle.PLAIN));
        } else if (value instanceof String || value instanceof Character || value instanceof Number ||
                    value instanceof Boolean) {
            emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                    value.toString(), null, null, DumperOptions.ScalarStyle.PLAIN));
        } else if (value instanceof JsonObject) {
            emitter.emit(new MappingStartEvent(null, null, true, null, null,
                    DumperOptions.FlowStyle.BLOCK));
            for (Map.Entry<String, Object> entry : ((JsonObject) value).entrySet()) {
                emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                        entry.getKey(), null, null, DumperOptions.ScalarStyle.PLAIN));
                write(emitter, entry.getValue());
            }
            emitter.emit(new MappingEndEvent(null, null));
        } else if (value instanceof Map) {
            emitter.emit(new MappingStartEvent(null, null, true, null, null,
                    DumperOptions.FlowStyle.BLOCK));
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                        entry.getKey().toString(), null, null, DumperOptions.ScalarStyle.PLAIN));
                write(emitter, entry.getValue());
            }
            emitter.emit(new MappingEndEvent(null, null));
        } else if (value instanceof JsonArray) {
            emitter.emit(new SequenceStartEvent(null, null, true, null, null,
                    DumperOptions.FlowStyle.BLOCK));
            for (Object elem : (JsonArray) value) {
                write(emitter, elem);
            }
            emitter.emit(new SequenceEndEvent(null, null));
        } else if (value instanceof List) {
            emitter.emit(new SequenceStartEvent(null, null, true, null, null,
                    DumperOptions.FlowStyle.BLOCK));
            for (Object v : (List<?>) value) {
                write(emitter, v);
            }
            emitter.emit(new SequenceEndEvent(null, null));
        } else if (value.getClass().isArray()) {
            emitter.emit(new SequenceStartEvent(null, null, true, null, null,
                    DumperOptions.FlowStyle.BLOCK));
            for (int i = 0; i < Array.getLength(value); i++) {
                write(emitter, Array.get(value, i));
            }
            emitter.emit(new SequenceEndEvent(null, null));
        } else {
            throw new IllegalStateException("Unsupported object type '" + object.getClass().getName() +
                    "', expected one of [JsonObject, JsonArray, String, Number, Boolean] or a type registered in " +
                    "ValueRegistry, or a Map/List/Array of such elements.");
        }
    }


}
