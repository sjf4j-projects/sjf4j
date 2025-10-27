package org.sjf4j.facades.snake;

import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.emitter.Emitter;
import org.yaml.snakeyaml.events.ImplicitTuple;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

import java.io.IOException;
import java.util.Map;

public class SimpleEmitter {


    public static void write(@NonNull Emitter emitter, Object value) throws IOException {
        if (value == null) {
            emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                    "null", null, null, DumperOptions.ScalarStyle.PLAIN));
        } else if (value instanceof JsonObject) {
            emitter.emit(new MappingStartEvent(null, null, true, null, null,
                    DumperOptions.FlowStyle.BLOCK));
            for (Map.Entry<String, Object> entry : ((JsonObject) value).entrySet()) {
                emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                        entry.getKey(), null, null, DumperOptions.ScalarStyle.PLAIN));
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
        } else {
            emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                    String.valueOf(value), null, null, DumperOptions.ScalarStyle.PLAIN));
        }
    }


}
