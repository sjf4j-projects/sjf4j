package org.sjf4j.facade.fastjson2;

import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.codec.BeanInfo;
import com.alibaba.fastjson2.codec.FieldInfo;
import com.alibaba.fastjson2.modules.ObjectReaderAnnotationProcessor;
import com.alibaba.fastjson2.modules.ObjectReaderModule;
import com.alibaba.fastjson2.modules.ObjectWriterModule;
import com.alibaba.fastjson2.reader.ObjectReader;
import com.alibaba.fastjson2.writer.ObjectWriter;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.Types;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;


public interface Fastjson2Module {

    class MyReaderModule implements ObjectReaderModule {
        @Override
        public ObjectReader<?> getObjectReader(Type type) {
            Class<?> rawClazz = Types.rawClazz(type);
            if (JsonArray.class.isAssignableFrom(rawClazz)) {
                return new JsonArrayReader<>(rawClazz);
            }
            NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
            if (vci != null) {
                return new NodeValueReader<>(vci);
            }
            return null;
        }

        @Override
        public void getFieldInfo(FieldInfo fieldInfo, Class objectClass, Field field) {
            ObjectReaderAnnotationProcessor annotationProcessor = getAnnotationProcessor();
            if (annotationProcessor != null) {
                annotationProcessor.getFieldInfo(fieldInfo, objectClass, field);
            }
            NodeProperty ann = field.getAnnotation(NodeProperty.class);
            if (ann != null) {
                String name = ann.value();
                if (name != null && name.length() > 0) {
                    fieldInfo.fieldName = ann.value();
                    fieldInfo.ignore = false; // Must false here
                }
                String[] aliases = ann.aliases();
                if (aliases != null && aliases.length > 0) {
                    fieldInfo.alternateNames = aliases;
                    fieldInfo.ignore = false; // Must false here
                }
            }
        }

        @Override
        public void getBeanInfo(BeanInfo beanInfo, Class<?> objectClass) {
            for (Constructor<?> ctor : objectClass.getDeclaredConstructors()) {
                if (ctor.isAnnotationPresent(NodeCreator.class)) {
                    beanInfo.creatorConstructor = ctor;
                    return;
                }
            }
        }

        @Override
        public ObjectReaderAnnotationProcessor getAnnotationProcessor() {
            return new ObjectReaderAnnotationProcessor() {
                @Override
                public void getFieldInfo(FieldInfo fieldInfo, Class objectClass, Constructor constructor,
                                         int paramIndex, Parameter parameter) {
                    NodeProperty ann = parameter.getAnnotation(NodeProperty.class);
                    if (ann != null) {
                        String name = ann.value();
                        if (name != null && name.length() > 0) {
                            fieldInfo.fieldName = ann.value();
                            fieldInfo.ignore = false; // Must false here
                        }
                        String[] aliases = ann.aliases();
                        if (aliases != null && aliases.length > 0) {
                            fieldInfo.alternateNames = aliases;
                            fieldInfo.ignore = false; // Must false here
                        }
                    }
                }
                @Override
                public void getFieldInfo(FieldInfo fieldInfo, Class objectClass, Method method,
                                         int paramIndex, Parameter parameter) {
                    NodeProperty ann = parameter.getAnnotation(NodeProperty.class);
                    if (ann != null) {
                        String name = ann.value();
                        if (name != null && name.length() > 0) {
                            fieldInfo.fieldName = ann.value();
                            fieldInfo.ignore = false; // Must false here
                        }
                        String[] aliases = ann.aliases();
                        if (aliases != null && aliases.length > 0) {
                            fieldInfo.alternateNames = aliases;
                            fieldInfo.ignore = false; // Must false here
                        }
                    }
                }
            };
        }
    }

//    class MyObjectReaderAnnotationProcessor implements ObjectReaderAnnotationProcessor {
//        @Override
//        public void getFieldInfo(FieldInfo fieldInfo, Class objectClass, Constructor constructor,
//                                 int paramIndex, Parameter parameter) {
//            NodeProperty ann = parameter.getAnnotation(NodeProperty.class);
//            if (ann != null) {
//                String name = ann.value();
//                if (name != null && name.length() > 0) {
//                    fieldInfo.fieldName = ann.value();
//                    fieldInfo.ignore = false; // Must false here
//                }
//                String[] aliases = ann.aliases();
//                if (aliases != null && aliases.length > 0) {
//                    fieldInfo.alternateNames = aliases;
//                    fieldInfo.ignore = false; // Must false here
//                }
//            }
//        }
//        @Override
//        public void getFieldInfo(FieldInfo fieldInfo, Class objectClass, Method method,
//                                 int paramIndex, Parameter parameter) {
//            NodeProperty ann = parameter.getAnnotation(NodeProperty.class);
//            if (ann != null) {
//                String name = ann.value();
//                if (name != null && name.length() > 0) {
//                    fieldInfo.fieldName = ann.value();
//                    fieldInfo.ignore = false; // Must false here
//                }
//                String[] aliases = ann.aliases();
//                if (aliases != null && aliases.length > 0) {
//                    fieldInfo.alternateNames = aliases;
//                    fieldInfo.ignore = false; // Must false here
//                }
//            }
//        }
//    }

    class JsonArrayReader<T extends JsonArray> implements ObjectReader<T> {
        private final NodeRegistry.PojoInfo pi;
        public JsonArrayReader(Class<?> clazz) {
            this.pi = clazz == JsonArray.class ? null : NodeRegistry.registerPojoOrElseThrow(clazz);
        }

        @SuppressWarnings("unchecked")
        @Override
        public T readObject(JSONReader reader, Type fieldType, Object fieldName, long features) {
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
        public NodeValueReader(NodeRegistry.ValueCodecInfo valueCodecInfo) {
            this.valueCodecInfo = valueCodecInfo;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T readObject(JSONReader reader, Type fieldType, Object fieldName, long features) {
            Object raw = reader.readAny();
            return (T) valueCodecInfo.decode(raw);
        }
    }


    /// Write
    class MyWriterModule implements ObjectWriterModule {
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
            return null;
        }

//        @SuppressWarnings("rawtypes")
//        @Override
//        public boolean createFieldWriters(ObjectWriterCreator creator,
//                                          Class objectType,
//                                          List<FieldWriter> fieldWriters) {
//            for (int i = 0; i < fieldWriters.size(); i++) {
//                FieldWriter fw = fieldWriters.get(i);
//                NodeField nf = fw.field.getAnnotation(NodeField.class);
//                if (nf != null && !nf.value().isEmpty()) {
//                    FieldWriter newFw = creator.createFieldWriter(nf.value(),
//                            fw.ordinal, fw.features, fw.format, fw.field);
//                    fieldWriters.set(i, newFw);
//                }
//            }
//            return false;
//        }

//        @Override
//        public ObjectWriterAnnotationProcessor getAnnotationProcessor() {
//            return new ObjectWriterAnnotationProcessor() {
//                @Override
//                public void getFieldInfo(BeanInfo beanInfo, FieldInfo fieldInfo, Class objectType, Field field) {
//                    NodeProperty nf = field.getAnnotation(NodeProperty.class);
//                    if (nf != null && !nf.value().isEmpty()) {
//                        fieldInfo.fieldName = nf.value();
//                        fieldInfo.ignore = false;
//                        fieldInfo.features |= FieldInfo.DISABLE_SMART_MATCH;
//                    }
//                }
//                @Override
//                public void getFieldInfo(BeanInfo beanInfo, FieldInfo fieldInfo, Class objectType, Method method) {
//                    fieldInfo.ignore = true;
//                }
//            };
//        }

    }

    class JsonObjectWriter implements ObjectWriter<JsonObject> {
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

    class JsonArrayWriter implements ObjectWriter<JsonArray> {
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
        public NodeValueWriter(NodeRegistry.ValueCodecInfo valueCodecInfo) {
            this.valueCodecInfo = valueCodecInfo;
        }

        @Override
        public void write(JSONWriter writer, Object object, Object fieldName, Type fieldType, long features) {
            Object raw = valueCodecInfo.encode(object);
            writer.writeAny(raw);
        }
    }

}
