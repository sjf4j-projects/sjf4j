package org.sjf4j.facades.snake;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.PojoRegistry;
import org.sjf4j.facades.FacadeReader;
import org.sjf4j.util.NumberUtil;
import org.sjf4j.util.StreamingUtil;
import org.sjf4j.util.TypeUtil;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.emitter.Emitter;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.DocumentEndEvent;
import org.yaml.snakeyaml.events.DocumentStartEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.ImplicitTuple;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;
import org.yaml.snakeyaml.events.StreamEndEvent;
import org.yaml.snakeyaml.events.StreamStartEvent;
import org.yaml.snakeyaml.parser.Parser;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * A SnakeYAML-specific, fully static implementation of the streaming JSON parser utilities.
 * <p>
 * This class is a specialized and performance-optimized version of {@link StreamingUtil},
 * designed to maximize JIT inlining and reduce dynamic dispatch overhead.
 * <p>
 * Unlike {@code StreamingUtil}, this implementation:
 * <ul>
 *   <li>Removes all polymorphism and interface indirection (no {@code FacadeReader} abstraction).</li>
 *   <li>Uses only {@code static} methods for direct JIT inlining.</li>
 *   <li>Avoids reflection or dynamic lookups in the parsing hot path.</li>
 * </ul>
 * <p>
 * In practice, this allows the JVM JIT compiler to aggressively inline calls,
 * perform constant folding, and optimize the parser loops specifically for the original API,
 * achieving lower per-call latency compared to the generic {@code StreamingUtil}.
 */
public class SnakeStreamingUtil {

    /// Read

    public static Object readNode(Parser parser, Type type) throws IOException {
        if (parser == null) throw new IllegalArgumentException("Parser must not be null");
        int tid = peekTokenId(parser);
        switch (tid) {
            case FacadeReader.ID_START_OBJECT:
                return readObject(parser, type);
            case FacadeReader.ID_START_ARRAY:
                return readArray(parser, type);
            case FacadeReader.ID_STRING:
                return nextString(parser);
//                return ConverterRegistry.tryPure2Wrap(nextString(parser), type);
            case FacadeReader.ID_NUMBER:
                return nextNumber(parser);
//                return ConverterRegistry.tryPure2Wrap(nextNumber(parser), type);
            case FacadeReader.ID_BOOLEAN:
                return nextBoolean(parser);
//                return ConverterRegistry.tryPure2Wrap(nextBoolean(parser), type);
            case FacadeReader.ID_NULL:
                nextNull(parser);
                return null;
//                return ConverterRegistry.tryPure2Wrap(null, type);
            default:
                throw new JsonException("Unexpected token id '" + tid + "'");
        }
    }


    public static Object readObject(Parser parser, Type type) throws IOException {
        if (parser == null) throw new IllegalArgumentException("Parser must not be null");
        Class<?> rawClazz = TypeUtil.getRawClass(type);

//        NodeConverter<?, ?> converter = ConverterRegistry.getConverter(rawClazz);
//        if (converter != null ) {
//            if (converter.getPureType() == JsonObject.class) {
//                JsonObject jo = new JsonObject();
//                startObject(parser);
//                while (hasNext(parser)) {
//                    String key = nextName(parser);
//                    Object value = readNode(parser, Object.class);
//                    jo.put(key, value);
//                }
//                endObject(parser);
//                return ((NodeConverter<Object, Object>) converter).pure2Wrap(jo);
//            } else {
//                throw new JsonException("Converter expects object '" + converter.getWrapType() +
//                        "' and node '" + converter.getPureType() + "', but got node 'JsonObject'");
//            }
//        }

        if (rawClazz == null || rawClazz.isAssignableFrom(JsonObject.class)) {
            JsonObject jo = new JsonObject();
            startObject(parser);
            while (hasNext(parser)) {
                String key = nextName(parser);
                Object value = readNode(parser, Object.class);
                jo.put(key, value);
            }
            endObject(parser);
            return jo;
        }

        if (rawClazz == Map.class) {
            Type valueType = TypeUtil.resolveTypeArgument(type, Map.class, 1);
            Map<String, Object> map = JsonConfig.global().mapSupplier.create();
            startObject(parser);
            while (hasNext(parser)) {
                String key = nextName(parser);
                Object value = readNode(parser, valueType);
                map.put(key, value);
            }
            endObject(parser);
            return map;
        }

        if (JsonObject.class.isAssignableFrom(rawClazz)) {
            PojoRegistry.PojoInfo pi = PojoRegistry.registerOrElseThrow(rawClazz);
            Map<String, PojoRegistry.FieldInfo> fields = pi.getFields();
            JsonObject jojo = (JsonObject) pi.newInstance();
            startObject(parser);
            while (hasNext(parser)) {
                String key = nextName(parser);
                PojoRegistry.FieldInfo fi = fields.get(key);
                if (fi != null) {
                    Object vv = readNode(parser, fi.getType());
                    fi.invokeSetter(jojo, vv);
                } else {
                    Object vv = readNode(parser, Object.class);
                    jojo.put(key, vv);
                }
            }
            endObject(parser);
            return jojo;
        }

        if (PojoRegistry.isPojo(rawClazz)) {
            PojoRegistry.PojoInfo pi = PojoRegistry.registerOrElseThrow(rawClazz);
            Map<String, PojoRegistry.FieldInfo> fields = pi.getFields();
            Object pojo = pi.newInstance();
            startObject(parser);
            while (hasNext(parser)) {
                String key = nextName(parser);
                PojoRegistry.FieldInfo fi = fields.get(key);
                if (fi != null) {
                    Object vv = readNode(parser, fi.getType());
                    fi.invokeSetter(pojo, vv);
                }
            }
            endObject(parser);
            return pojo;
        }

        throw new JsonException("Unsupported type: " + type);
    }


    public static Object readArray(Parser parser, Type type) throws IOException {
        if (parser == null) throw new IllegalArgumentException("Parser must not be null");
        Class<?> rawClazz = TypeUtil.getRawClass(type);

//        NodeConverter<?, ?> converter = ConverterRegistry.getConverter(rawClazz);
//        if (converter != null ) {
//            if (converter.getPureType() == JsonArray.class) {
//                JsonArray ja = new JsonArray();
//                startArray(parser);
//                while (hasNext(parser)) {
//                    Object value = readNode(parser, Object.class);
//                    ja.add(value);
//                }
//                endArray(parser);
//                return ((NodeConverter<Object, Object>) converter).pure2Wrap(ja);
//            } else {
//                throw new JsonException("Converter expects object '" + converter.getWrapType() +
//                        "' and node '" + converter.getPureType() + "', but got node 'JsonArray'");
//            }
//        }

        if (rawClazz == null || rawClazz.isAssignableFrom(JsonArray.class)) {
            JsonArray ja = new JsonArray();
            startArray(parser);
            while (hasNext(parser)) {
                Object value = readNode(parser, Object.class);
                ja.add(value);
            }
            endArray(parser);
            return ja;
        }

        if (rawClazz == List.class) {
            Type valueType = TypeUtil.resolveTypeArgument(type, List.class, 0);
            List<Object> list = new ArrayList<>();
            startArray(parser);
            while (hasNext(parser)) {
                Object value = readNode(parser, valueType);
                list.add(value);
            }
            endArray(parser);
            return list;
        }

        if (rawClazz.isArray()) {
            Class<?> valueClazz = rawClazz.getComponentType();
            List<Object> list = new ArrayList<>();
            startArray(parser);
            while (hasNext(parser)) {
                Object value = readNode(parser, valueClazz);
                list.add(value);
            }
            endArray(parser);

            Object array = Array.newInstance(valueClazz, list.size());
            for (int i = 0; i < list.size(); i++) {
                Array.set(array, i, list.get(i));
            }
            return array;
        }

        throw new JsonException("Unsupported type: " + type);
    }


    /// Reader

    public static int peekTokenId(Parser parser) throws IOException {
        Event event = parser.peekEvent();
        if (event instanceof MappingStartEvent) {
            return FacadeReader.ID_START_OBJECT;
        } else if (event instanceof MappingEndEvent) {
            return FacadeReader.ID_END_OBJECT;
        } else if (event instanceof SequenceStartEvent) {
            return FacadeReader.ID_START_ARRAY;
        } else if (event instanceof SequenceEndEvent) {
            return FacadeReader.ID_END_ARRAY;
        } else if (event instanceof ScalarEvent) {
            ScalarEvent se = (ScalarEvent) event;
            String value = se.getValue();
            String tag = se.getTag();
            if (value == null || value.isEmpty() || "tag:yaml.org,2002:null".equals(tag) ||
                    value.equalsIgnoreCase("null") || value.equals("~")) {
                return FacadeReader.ID_NULL;
            }
            if ("tag:yaml.org,2002:str".equals(tag) || "!".equals(tag)) {
                return FacadeReader.ID_STRING;
            }
            String low = value.toLowerCase();
            if (low.equals("true") || low.equals("yes") || low.equals("on")) {
                return FacadeReader.ID_BOOLEAN;
            }
            if (low.equals("false") || low.equals("no") || low.equals("off")) {
                return FacadeReader.ID_BOOLEAN;
            }
            if (NumberUtil.isNumeric(value)) {
                return FacadeReader.ID_NUMBER;
            } else {
                return FacadeReader.ID_STRING;
            }
        } else if (event instanceof AliasEvent) {
            throw new JsonException("YAML anchors/aliases not supported.");
        } else {
            return FacadeReader.ID_UNKNOWN;
        }
    }

    public static void startDocument(Parser parser) throws IOException {
        if (!(parser.getEvent() instanceof StreamStartEvent)) throw new IllegalStateException("Malformed YAML");
        if (!(parser.getEvent() instanceof DocumentStartEvent)) throw new IllegalStateException("Malformed YAML");
    }

    public static void endDocument(Parser parser) throws IOException {
        if (!(parser.getEvent() instanceof DocumentEndEvent)) throw new IllegalStateException("Malformed YAML");
        if (!(parser.getEvent() instanceof StreamEndEvent)) throw new IllegalStateException("Malformed YAML");
    }

    public static void startObject(Parser parser) throws IOException {
        parser.getEvent();
    }

    public static void endObject(Parser parser) throws IOException {
        parser.getEvent();
    }

    public static void startArray(Parser parser) throws IOException {
        parser.getEvent();
    }

    public static void endArray(Parser parser) throws IOException {
        parser.getEvent();
    }

    public static String nextName(Parser parser) throws IOException {
        ScalarEvent se = (ScalarEvent) parser.getEvent();
        return se.getValue();
    }

    public static String nextString(Parser parser) throws IOException {
        ScalarEvent se = (ScalarEvent) parser.getEvent();
        return se.getValue();
    }

    public static Number nextNumber(Parser parser) throws IOException {
        ScalarEvent se = (ScalarEvent) parser.getEvent();
        return NumberUtil.toNumber(se.getValue());
    }

    public static Boolean nextBoolean(Parser parser) throws IOException {
        ScalarEvent se = (ScalarEvent) parser.getEvent();
        String value = se.getValue();
        String low = value.toLowerCase();
        if (low.equals("true") || low.equals("yes") || low.equals("on")) {
            return true;
        }
        if (low.equals("false") || low.equals("no") || low.equals("off")) {
            return false;
        }
        throw new JsonException("Expect a Boolean but got '" + value + "'");
    }

    public static void nextNull(Parser parser) throws IOException {
        ScalarEvent se = (ScalarEvent) parser.getEvent();
        String value = se.getValue();
        String tag = se.getTag();
        if (value == null || value.isEmpty() || "tag:yaml.org,2002:null".equals(tag) ||
                value.equalsIgnoreCase("null") || value.equals("~")) {
            return;
        }
        throw new JsonException("Expect a Null but got '" + value + "'");
    }

    public static boolean hasNext(Parser parser) throws IOException {
        Event event = parser.peekEvent();
        return !(event instanceof MappingEndEvent) && !(event instanceof SequenceEndEvent);
    }
    

    /// Write

    public static void writeNode(Emitter emitter, Object node) throws IOException {
        if (emitter == null) throw new IllegalArgumentException("Emitter must not be null");
//        node = ConverterRegistry.tryWrap2Pure(node);
        if (node == null) {
            writeNull(emitter);
        } else if (node instanceof CharSequence || node instanceof Character) {
            writeValue(emitter, node.toString());
        } else if (node instanceof Number) {
            writeValue(emitter, (Number) node);
        } else if (node instanceof Boolean) {
            writeValue(emitter, (Boolean) node);
        } else if (node instanceof JsonObject) {
            startObject(emitter);
            ((JsonObject) node).forEach((k, v) -> {
                try {
                    writeName(emitter, k);
                    writeNode(emitter, v);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            endObject(emitter);
        } else if (node instanceof Map) {
            startObject(emitter);
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
                writeName(emitter, entry.getKey().toString());
                writeNode(emitter, entry.getValue());
            }
            endObject(emitter);
        } else if (node instanceof JsonArray) {
            startArray(emitter);
            JsonArray ja = (JsonArray) node;
            for (Object v : ja) {
                writeNode(emitter, v);
            }
            endArray(emitter);
        } else if (node instanceof List) {
            startArray(emitter);
            List<?> list = (List<?>) node;
            for (Object v : list) {
                writeNode(emitter, v);
            }
            endArray(emitter);
        } else if (node.getClass().isArray()) {
            startArray(emitter);
            for (int i = 0; i < Array.getLength(node); i++) {
                writeNode(emitter, Array.get(node, i));
            }
            endArray(emitter);
        } else if (PojoRegistry.isPojo(node.getClass())) {
            startObject(emitter);
            for (Map.Entry<String, PojoRegistry.FieldInfo> entry :
                    PojoRegistry.getPojoInfo(node.getClass()).getFields().entrySet()) {
                writeName(emitter, entry.getKey());
                Object vv = entry.getValue().invokeGetter(node);
                writeNode(emitter, vv);
            }
            endObject(emitter);
        } else {
            throw new IllegalStateException("Unsupported node type '" + node.getClass().getName() +
                    "', expected one of [JsonObject, JsonArray, String, Number, Boolean] or a type registered in " +
                    "ConverterRegistry or a valid POJO/JOJO, or a Map/List/Array of such elements.");
        }
    }

    /// Writer

    public static void startDocument(Emitter emitter) throws IOException {
        emitter.emit(new StreamStartEvent(null, null));
        emitter.emit(new DocumentStartEvent(null, null, false, null, null));
    }

    public static void endDocument(Emitter emitter) throws IOException {
        emitter.emit(new DocumentEndEvent(null, null, false));
        emitter.emit(new StreamEndEvent(null, null));
    }

    public static void startObject(Emitter emitter) throws IOException {
        emitter.emit(new MappingStartEvent(null, null, true, null, null,
                DumperOptions.FlowStyle.BLOCK));
    }

    public static void endObject(Emitter emitter) throws IOException {
        emitter.emit(new MappingEndEvent(null, null));
    }

    public static void startArray(Emitter emitter) throws IOException {
        emitter.emit(new SequenceStartEvent(null, null, true, null, null,
                DumperOptions.FlowStyle.BLOCK));
    }

    public static void endArray(Emitter emitter) throws IOException {
        emitter.emit(new SequenceEndEvent(null, null));
    }

    public static void writeName(Emitter emitter, String name) throws IOException {
        emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                name, null, null, DumperOptions.ScalarStyle.PLAIN));
    }

    public static void writeValue(Emitter emitter, String value) throws IOException {
        emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                value, null, null, DumperOptions.ScalarStyle.PLAIN));
    }

    public static void writeValue(Emitter emitter, Number value) throws IOException {
        emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                value.toString(), null, null, DumperOptions.ScalarStyle.PLAIN));
    }

    public static void writeValue(Emitter emitter, Boolean value) throws IOException {
        emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                value.toString(), null, null, DumperOptions.ScalarStyle.PLAIN));
    }

    public static void writeNull(Emitter emitter) throws IOException {
        emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                "null", null, null, DumperOptions.ScalarStyle.PLAIN));
    }

}
