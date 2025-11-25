package org.sjf4j.facades.fastjson2;

import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.modules.ObjectReaderModule;
import com.alibaba.fastjson2.reader.FieldReader;
import com.alibaba.fastjson2.reader.ObjectReader;
import com.alibaba.fastjson2.reader.ObjectReaderAdapter;
import com.alibaba.fastjson2.reader.ObjectReaderBean;
import com.alibaba.fastjson2.reader.ObjectReaderCreator;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
import com.alibaba.fastjson2.schema.JSONSchema;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.util.TypeUtil;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;


@Slf4j
public class Fastjson2Module {

    public static class MyObjectReaderModule implements ObjectReaderModule {

        @Override
        public ObjectReader<?> getObjectReader(Type type) {
            Class<?> clazz = TypeUtil.getRawClass(type);

            if (!JsonObject.class.isAssignableFrom(clazz)) {
                return null;
            }

            ObjectReader<?> objectReader = ObjectReaderCreator.INSTANCE.createObjectReader(type);
            return new MyObjectReader<>(objectReader);

//            Supplier<?> creator = ObjectReaderCreator.INSTANCE.createSupplier(clazz);
//            Supplier<?> creator = JSONFactory.getContextReaderCreator().createSupplier(clazz);

            //FIXME: JSONReader.features
//            return new MyObjectReaderBean<>(clazz, creator, clazz.getName(), 0, null, null);
        }

    }

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
        public JsonArray readObject(JSONReader reader,
                                    Type fieldType,
                                    Object fieldName,
                                    long features) {
            // 必须是 [
            if (!reader.nextIfArrayStart()) {
                throw new JSONException(reader.info("expect '['"));
            }

            JsonArray array = new JsonArray();

            while (!reader.nextIfArrayEnd()) {
                Object value = reader.readAny();
                array.add(value);
            }

            return array;
        }

        @Override
        public Class<JsonArray> getObjectClass() {
            return JsonArray.class;
        }
    }


    public static class MyObjectReader<T> implements ObjectReader<T> {
        final ObjectReader<T> origin;

        public MyObjectReader(ObjectReader<T> origin) {
            this.origin = origin;
        }

        @Override
        public T readObject(JSONReader reader, Type fieldType, Object fieldName, long features) {
            T obj = origin.readObject(reader, fieldType, fieldName, features);

            // Fastjson2 已经处理了常规字段，现在处理 extra
            // 注意：extra 是在 reader 流程中回调的
            return obj;
        }

        @Override
        public void acceptExtra(Object object, String fieldName, Object fieldValue, long features) {
            log.info("acceptExtra name={}, value={}", fieldName, fieldValue);
            if (object instanceof JsonObject) {
                ((JsonObject) object).put(fieldName, fieldValue);
            }
        }


        @Override
        public T createInstance(long features) {
            return origin.createInstance(features);
        }

        @Override
        public T createInstanceNoneDefaultConstructor(Map<Long, Object> values) {
            return origin.createInstanceNoneDefaultConstructor(values);
        }

        @Override
        public long getFeatures() {
            return origin.getFeatures();
        }

        @Override
        public String getTypeKey() {
            return origin.getTypeKey();
        }

        @Override
        public long getTypeKeyHash() {
            return origin.getTypeKeyHash();
        }

        @Override
        public Class<T> getObjectClass() {
            return origin.getObjectClass();
        }

        @Override
        public FieldReader getFieldReader(long hashCode) {
            return origin.getFieldReader(hashCode);
        }

        @Override
        public FieldReader getFieldReaderLCase(long hashCode) {
            return origin.getFieldReaderLCase(hashCode);
        }

        @Override
        public boolean setFieldValue(Object object, String fieldName, long fieldNameHashCode, int value) {
            return origin.setFieldValue(object, fieldName, fieldNameHashCode, value);
        }

        @Override
        public boolean setFieldValue(Object object, String fieldName, long fieldNameHashCode, long value) {
            return origin.setFieldValue(object, fieldName, fieldNameHashCode, value);
        }

        @Override
        public FieldReader getFieldReader(String fieldName) {
            return origin.getFieldReader(fieldName);
        }

        @Override
        public boolean setFieldValue(Object object, String fieldName, Object value) {
            return origin.setFieldValue(object, fieldName, value);
        }

        @Override
        public Function getBuildFunction() {
            return origin.getBuildFunction();
        }

        @Override
        public ObjectReader autoType(JSONReader.Context context, long typeHash) {
            return origin.autoType(context, typeHash);
        }

        @Override
        public ObjectReader autoType(ObjectReaderProvider provider, long typeHash) {
            return origin.autoType(provider, typeHash);
        }

        @Override
        public T readJSONBObject(JSONReader jsonReader, Type fieldType, Object fieldName, long features) {
            return origin.readJSONBObject(jsonReader, fieldType, fieldName, features);
        }

        @Override
        public T readArrayMappingJSONBObject(JSONReader jsonReader, Type fieldType, Object fieldName, long features) {
            return origin.readArrayMappingJSONBObject(jsonReader, fieldType, fieldName, features);
        }

        @Override
        public T readArrayMappingObject(JSONReader jsonReader, Type fieldType, Object fieldName, long features) {
            return origin.readArrayMappingObject(jsonReader, fieldType, fieldName, features);
        }


    }
}
