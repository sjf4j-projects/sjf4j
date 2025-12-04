package org.sjf4j.util;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

public final class RecordUtil {

    private static final boolean RECORD_SUPPORTED;
    private static final Method IS_RECORD_METHOD;
    private static final Method GET_RECORD_COMPONENTS_METHOD;

    static {
        boolean supported = false;
        Method isRecord = null;
        Method getComponents = null;
        try {
            isRecord = Class.class.getMethod("isRecord");
            getComponents = Class.class.getMethod("getRecordComponents");
            supported = true;
        } catch (NoSuchMethodException ignore) {
            // JDK8
        }
        RECORD_SUPPORTED = supported;
        IS_RECORD_METHOD = isRecord;
        GET_RECORD_COMPONENTS_METHOD = getComponents;
    }

    public static boolean isRecordClass(Type clazz) {
        if (!RECORD_SUPPORTED) return false;
        try {
            return (boolean) IS_RECORD_METHOD.invoke(clazz);
        } catch (Exception e) {
            return false;
        }
    }

    public static Object[] getRecordComponents(Type clazz) {
        if (!RECORD_SUPPORTED) return null;
        try {
            return (Object[]) GET_RECORD_COMPONENTS_METHOD.invoke(clazz);
        } catch (Exception e) {
            return null;
        }
    }
}
