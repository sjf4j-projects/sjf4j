package org.sjf4j.facade.fastjson2;

import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.modules.ObjectWriterModule;
import com.alibaba.fastjson2.reader.ObjectReader;
import com.alibaba.fastjson2.writer.ObjectWriter;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

import java.lang.reflect.Type;


public class Fastjson2Module {

//    public static class MyObjectReaderModule implements ObjectReaderModule {
//
//        @Override
//        public ObjectReader<?> getObjectReader(Type type) {
//            Class<?> clazz = TypeUtil.getRawClass(type);
//
//            if (!JsonObject.class.isAssignableFrom(clazz)) {
//                return null;
//            }
//
//            ObjectReader<?> objectReader = ObjectReaderCreator.INSTANCE.createObjectReader(type);
//            return new MyObjectReader<>(objectReader);
//
//        }
//
//    }

//    public static class MyObjectReaderBean<T> extends ObjectReaderAdapter<T> {
//
//        protected MyObjectReaderBean(Class<?> objectClass, Supplier<T> creator, String typeName, long features,
//                                     JSONSchema schema, Function buildFunction) {
//            super(objectClass, creator, typeName, features, schema, buildFunction);
//        }
//
//        @Override
//        protected void processExtra(JSONReader reader, Object object) {
//            String name = reader.getFieldName();
//            Object val = reader.readAny(); // 支持任意 JSON
//
//            if (object instanceof JsonObject) {
//                ((JsonObject) object).put(name, val);
//            }
//        }
//    }

    public static class JsonArrayReader implements ObjectReader<JsonArray> {

        @Override
        public JsonArray readObject(JSONReader reader, Type fieldType, Object fieldName, long features) {
            if (!reader.nextIfArrayStart()) {
                throw new JSONException(reader.info("expect '['"));
            }
            JsonArray ja = new JsonArray();
            while (!reader.nextIfArrayEnd()) {
                Object value = reader.readAny();
                ja.add(value);
            }
            return ja;
        }

        @Override
        public Class<JsonArray> getObjectClass() {
            return JsonArray.class;
        }

    }

    /// Write

    public static class MyObjectWriterModule implements ObjectWriterModule {
        @Override
        public ObjectWriter<?> getObjectWriter(Type objectType, Class objectClass) {
            if (JsonObject.class.isAssignableFrom(objectClass)) {
                return new JojoObjectWriter();
            }
            if (JsonArray.class.isAssignableFrom(objectClass)) {
                return new JojaObjectWriter();
            }
            return null;
        }
    }

    public static class JojoObjectWriter implements ObjectWriter<JsonObject> {

        @Override
        public void write(JSONWriter writer, Object object, Object fieldName,
                          Type fieldType, long features) {
            JsonObject jo = (JsonObject) object;
            writer.startObject();
            jo.forEach((k, v) -> {
                writer.writeName(k);
                writer.writeColon();
                writer.writeAny(v);
            });
            writer.endObject();
        }

    }


    public static class JojaObjectWriter implements ObjectWriter<JsonArray> {

        @Override
        public void write(JSONWriter writer, Object object, Object fieldName,
                          Type fieldType, long features) {
            JsonArray ja = (JsonArray) object;
            writer.startArray();
            ja.forEach((i, v) -> {
                if (i > 0) writer.writeComma();
                writer.writeAny(v);
            });
            writer.endArray();
        }

    }


}
