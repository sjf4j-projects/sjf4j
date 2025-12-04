package org.sjf4j.facades.snake;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.PojoRegistry;
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
import java.util.List;
import java.util.Map;

public class SnakeEmitter {


    public static void writeAny(Emitter emitter, Object value) throws IOException {
        if (emitter == null) throw new IllegalArgumentException("Emitter must not be null");
        if (value == null) {
            emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                    "null", null, null, DumperOptions.ScalarStyle.PLAIN));
        } else if (value instanceof CharSequence || value instanceof Character ||
                value instanceof Number || value instanceof Boolean) {
            emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                    value.toString(), null, null, DumperOptions.ScalarStyle.PLAIN));
        } else if (value instanceof JsonObject) {
            emitter.emit(new MappingStartEvent(null, null, true, null, null,
                    DumperOptions.FlowStyle.BLOCK));
            ((JsonObject) value).forEach((k, v) -> {
                try {
                    emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                            k, null, null, DumperOptions.ScalarStyle.PLAIN));
                    writeAny(emitter, v);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            emitter.emit(new MappingEndEvent(null, null));
        } else if (value instanceof Map) {
            emitter.emit(new MappingStartEvent(null, null, true, null, null,
                    DumperOptions.FlowStyle.BLOCK));
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                        entry.getKey().toString(), null, null, DumperOptions.ScalarStyle.PLAIN));
                writeAny(emitter, entry.getValue());
            }
            emitter.emit(new MappingEndEvent(null, null));
        } else if (value instanceof JsonArray) {
            emitter.emit(new SequenceStartEvent(null, null, true, null, null,
                    DumperOptions.FlowStyle.BLOCK));
            for (Object elem : (JsonArray) value) {
                writeAny(emitter, elem);
            }
            emitter.emit(new SequenceEndEvent(null, null));
        } else if (value instanceof List) {
            emitter.emit(new SequenceStartEvent(null, null, true, null, null,
                    DumperOptions.FlowStyle.BLOCK));
            for (Object v : (List<?>) value) {
                writeAny(emitter, v);
            }
            emitter.emit(new SequenceEndEvent(null, null));
        } else if (value.getClass().isArray()) {
            emitter.emit(new SequenceStartEvent(null, null, true, null, null,
                    DumperOptions.FlowStyle.BLOCK));
            for (int i = 0; i < Array.getLength(value); i++) {
                writeAny(emitter, Array.get(value, i));
            }
            emitter.emit(new SequenceEndEvent(null, null));
        } else if (PojoRegistry.isPojo(value.getClass())) {
            emitter.emit(new MappingStartEvent(null, null, true, null, null,
                    DumperOptions.FlowStyle.BLOCK));
            for (Map.Entry<String, PojoRegistry.FieldInfo> entry :
                    PojoRegistry.getPojoInfo(value.getClass()).getFields().entrySet()) {
                emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                        entry.getKey(), null, null, DumperOptions.ScalarStyle.PLAIN));
                Object vv = entry.getValue().invokeGetter(value);
                writeAny(emitter, vv);
            }
            emitter.emit(new MappingEndEvent(null, null));
        } else {
            throw new IllegalStateException("Unsupported object type '" + value.getClass().getName() +
                    "', expected one of [JsonObject, JsonArray, String, Number, Boolean] or a type registered in " +
                    "ObjectRegistry or a valid POJO, or a Map/List/Array of such elements.");
        }
    }


}
