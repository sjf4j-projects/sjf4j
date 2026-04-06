package org.sjf4j.facade.fastjson2;

import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.codec.BeanInfo;
import com.alibaba.fastjson2.codec.FieldInfo;
import com.alibaba.fastjson2.modules.ObjectReaderAnnotationProcessor;
import com.alibaba.fastjson2.modules.ObjectReaderModule;
import com.alibaba.fastjson2.modules.ObjectWriterAnnotationProcessor;
import com.alibaba.fastjson2.modules.ObjectWriterModule;
import com.alibaba.fastjson2.reader.ObjectReader;
import com.alibaba.fastjson2.writer.ObjectWriter;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.ReflectUtil;
import org.sjf4j.node.Types;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;


/**
 * Fastjson2 module integration for JsonObject/JsonArray and @NodeValue types.
 */
public interface Fastjson2Module {

    /**
     * Reader module for JsonArray and @NodeValue decoding.
     */
    class MyReaderModule implements ObjectReaderModule {
        /**
         * Returns custom reader for supported framework types.
         */
        @Override
        public ObjectReader<?> getObjectReader(Type type) {
            Class<?> rawClazz = Types.rawClazz(type);
            if (JsonArray.class.isAssignableFrom(rawClazz)) {
                return new JsonArrayReader<>(rawClazz);
            }
            if (JsonObject.class.isAssignableFrom(rawClazz)) {
                return new JsonObjectReader<>(type, rawClazz);
            }
            NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
            if (ti.anyOfInfo != null) {
                return new AnyOfReader<>(ti.anyOfInfo);
            }
            if (ti.valueCodecInfo != null) {
                return new NodeValueReader<>(ti.valueCodecInfo);
            }
            if (ti.requiresFrameworkReader()) {
                return new PojoReader<>(ti.pojoInfo);
            }

            return null;
        }

        /**
         * Applies NodeProperty renaming and aliases on fields.
         */
        @Override
        public void getFieldInfo(FieldInfo fieldInfo, Class objectClass, Field field) {
            ObjectReaderAnnotationProcessor annotationProcessor = getAnnotationProcessor();
            if (annotationProcessor != null) {
                annotationProcessor.getFieldInfo(fieldInfo, objectClass, field);
            }
            String name = ReflectUtil.getExplicitName(field);
            if (name != null && name.length() > 0) {
                fieldInfo.fieldName = name;
                fieldInfo.ignore = false;
            }
            String[] aliases = ReflectUtil.getAliases(field);
            if (aliases != null && aliases.length > 0) {
                fieldInfo.alternateNames = aliases;
                fieldInfo.ignore = false;
            }
        }

        /**
         * Detects NodeCreator constructor for object creation.
         */
        @Override
        public void getBeanInfo(BeanInfo beanInfo, Class<?> objectClass) {
            for (Constructor<?> ctor : objectClass.getDeclaredConstructors()) {
                if (ctor.isAnnotationPresent(NodeCreator.class)) {
                    beanInfo.creatorConstructor = ctor;
                    return;
                }
            }
        }

        /**
         * Returns annotation processor for constructor/method parameters.
         */
        @Override
        public ObjectReaderAnnotationProcessor getAnnotationProcessor() {
            return new ObjectReaderAnnotationProcessor() {
                @Override
                public void getFieldInfo(FieldInfo fieldInfo, Class objectClass, Constructor constructor,
                                         int paramIndex, Parameter parameter) {
                    applyNameAndAliases(fieldInfo, parameter);
                }

                @Override
                public void getFieldInfo(FieldInfo fieldInfo, Class objectClass, Method method,
                                         int paramIndex, Parameter parameter) {
                    applyNameAndAliases(fieldInfo, parameter);
                }
            };
        }

        private static void applyNameAndAliases(FieldInfo fieldInfo, Parameter parameter) {
            String name = ReflectUtil.getExplicitName(parameter);
            if (name != null && name.length() > 0) {
                fieldInfo.fieldName = name;
                fieldInfo.ignore = false;
            }
            String[] aliases = ReflectUtil.getAliases(parameter);
            if (aliases != null && aliases.length > 0) {
                fieldInfo.alternateNames = aliases;
                fieldInfo.ignore = false;
            }
        }
    }

    class JsonObjectReader<T extends JsonObject> implements ObjectReader<T> {
        private final Type ownerType;
        private final NodeRegistry.PojoInfo pi;
        /**
         * Creates reader for JsonArray or JsonArray subclass.
         */
        public JsonObjectReader(Type ownerType, Class<?> clazz) {
            this.ownerType = ownerType;
            this.pi = clazz == JsonObject.class ? null : NodeRegistry.registerPojoOrElseThrow(clazz);
        }

        /**
         * Reads one JSON array into framework JsonArray type.
         */
        @SuppressWarnings("unchecked")
        @Override
        public T readObject(JSONReader reader, Type fieldType, Object fieldName, long features) {
            if (reader.nextIfNull()) return null;
            if (pi != null) {
                if (!reader.isObject()) throw new JSONException(reader.info("expect '{'"));
                try {
                    Type resolvedOwnerType = fieldType != null ? fieldType : (ownerType != null ? ownerType : pi.clazz);
                    return (T) Fastjson2StreamingIO.readPojo(reader, resolvedOwnerType, Types.rawBox(resolvedOwnerType), pi);
                } catch (IOException e) {
                    throw new JSONException(reader.info("JsonObjectReader.readObject() failed"), e);
                }
            }
            if (!reader.nextIfObjectStart()) throw new JSONException(reader.info("expect '{'"));
            JsonObject jo = new JsonObject();
            while (!reader.nextIfObjectEnd()) {
                String key = reader.readFieldName();
                Object value = reader.readAny();
                jo.put(key, value);
            }
            return (T) jo;
        }
    }


    class JsonArrayReader<T extends JsonArray> implements ObjectReader<T> {
        private final NodeRegistry.PojoInfo pi;
        /**
         * Creates reader for JsonArray or JsonArray subclass.
         */
        public JsonArrayReader(Class<?> clazz) {
            this.pi = clazz == JsonArray.class ? null : NodeRegistry.registerPojoOrElseThrow(clazz);
        }

        /**
         * Reads one JSON array into framework JsonArray type.
         */
        @SuppressWarnings("unchecked")
        @Override
        public T readObject(JSONReader reader, Type fieldType, Object fieldName, long features) {
            if (reader.nextIfNull()) return null;
            if (!reader.nextIfArrayStart()) throw new JSONException(reader.info("expect '['"));
            T ja = pi == null ? (T) new JsonArray() : (T) pi.creatorInfo.forceNewPojo();
            while (!reader.nextIfArrayEnd()) {
                Object value = reader.read(ja.elementType());
                ja.add(value);
            }
            return ja;
        }
    }

    class NodeValueReader<T> implements ObjectReader<T> {
        private final NodeRegistry.ValueCodecInfo valueCodecInfo;
        /**
         * Creates reader backed by ValueCodec metadata.
         */
        public NodeValueReader(NodeRegistry.ValueCodecInfo valueCodecInfo) {
            this.valueCodecInfo = valueCodecInfo;
        }

        /**
         * Reads raw value and decodes via ValueCodec.
         */
        @SuppressWarnings("unchecked")
        @Override
        public T readObject(JSONReader reader, Type fieldType, Object fieldName, long features) {
            Object raw = reader.readAny();
            return (T) valueCodecInfo.rawToValue(raw);
        }
    }

    class AnyOfReader<T> implements ObjectReader<T> {
        private final NodeRegistry.AnyOfInfo anyOfInfo;
        /**
         * Creates reader for JsonArray or JsonArray subclass.
         */
        public AnyOfReader(NodeRegistry.AnyOfInfo anyOfInfo) {
            this.anyOfInfo = anyOfInfo;
        }

        /**
         * Reads one JSON array into framework JsonArray type.
         */
        @SuppressWarnings("unchecked")
        @Override
        public T readObject(JSONReader reader, Type fieldType, Object fieldName, long features) {
            if (reader.nextIfNull()) return null;
            return (T) Fastjson2StreamingIO.readAnyOf(reader, anyOfInfo);
        }
    }

    class PojoReader<T> implements ObjectReader<T> {
        private final NodeRegistry.PojoInfo pi;
        /**
         * Creates reader for JsonArray or JsonArray subclass.
         */
        public PojoReader(NodeRegistry.PojoInfo pi) {
            this.pi = pi;
        }

        /**
         * Reads one JSON array into framework JsonArray type.
         */
        @SuppressWarnings("unchecked")
        @Override
        public T readObject(JSONReader reader, Type fieldType, Object fieldName, long features) {
            if (reader.nextIfNull()) return null;
            try {
                Type ownerType = fieldType != null ? fieldType : pi.clazz;
                return (T) Fastjson2StreamingIO.readPojo(reader, ownerType, Types.rawBox(ownerType), pi);
            } catch (IOException e) {
                throw new JSONException(reader.info("PojoReader.readObject() failed"), e);
            }
        }
    }



    /// Write
    /**
     * Writer module for JsonObject/JsonArray and @NodeValue encoding.
     */
    class MyWriterModule implements ObjectWriterModule {
        /**
         * Returns custom writer for supported framework types.
         */
        @Override
        public ObjectWriter<?> getObjectWriter(Type objectType, Class objectClass) {
            if (JsonObject.class.isAssignableFrom(objectClass)) {
                return new JsonObjectWriter();
            }
            if (JsonArray.class.isAssignableFrom(objectClass)) {
                return new JsonArrayWriter();
            }
            NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(objectClass);
            if (vci != null) {
                return new NodeValueWriter<>(vci);
            }
            NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(objectClass);
            if (ti.requiresFrameworkWriter()) {
                return new JsonObjectWriter();
            }
            return null;
        }

        @Override
        public ObjectWriterAnnotationProcessor getAnnotationProcessor() {
            return new ObjectWriterAnnotationProcessor() {
                @Override
                public void getFieldInfo(BeanInfo beanInfo, FieldInfo fieldInfo, Class objectType, Field field) {
                    String name = ReflectUtil.getExplicitName(field);
                    if (name != null && name.length() > 0) {
                        fieldInfo.fieldName = name;
                        fieldInfo.ignore = false;
                    }
                }

                @Override
                public void getFieldInfo(BeanInfo beanInfo, FieldInfo fieldInfo, Class objectType, Method method) {
                }
            };
        }

    }

    class JsonObjectWriter implements ObjectWriter<Object> {
        @Override
        public void write(JSONWriter writer, Object object, Object fieldName,
                          Type fieldType, long features) {
            try {
                Fastjson2StreamingIO.writeNode(writer, object);
            } catch (IOException e) {
                throw new JSONException(writer.toString(), e);
            }
        }
    }

    class JsonArrayWriter implements ObjectWriter<JsonArray> {
        /**
         * Writes JsonArray elements as JSON array.
         */
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

    class NodeValueWriter<T> implements ObjectWriter<T> {
        private final NodeRegistry.ValueCodecInfo valueCodecInfo;
        /**
         * Creates writer backed by ValueCodec metadata.
         */
        public NodeValueWriter(NodeRegistry.ValueCodecInfo valueCodecInfo) {
            this.valueCodecInfo = valueCodecInfo;
        }

        /**
         * Encodes value via codec and writes raw form.
         */
        @Override
        public void write(JSONWriter writer, Object object, Object fieldName, Type fieldType, long features) {
            Object raw = valueCodecInfo.valueToRaw(object);
            writer.writeAny(raw);
        }
    }

}
