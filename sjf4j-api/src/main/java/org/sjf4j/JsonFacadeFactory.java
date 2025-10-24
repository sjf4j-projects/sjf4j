package org.sjf4j;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import lombok.Setter;
import org.sjf4j.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.gson.GsonJsonFacade;
import org.sjf4j.jackson.JacksonJsonFacade;


public class JsonFacadeFactory {

    private static boolean fastjson2Present;
    private static boolean jacksonPresent;
    private static boolean gsonPresent;

    @Setter
    private static JsonFacade defaultJsonFacade;

    static {
        ClassLoader loader = JsonFacadeFactory.class.getClassLoader();

        try {
            loader.loadClass("com.fasterxml.jackson.databind.ObjectMapper");
            jacksonPresent = true;
        } catch (Throwable e) {
            jacksonPresent = false;
        }

        try {
            loader.loadClass("com.google.gson.Gson");
            gsonPresent = true;
        } catch (Throwable e) {
            gsonPresent = false;
        }

        try {
            loader.loadClass("com.alibaba.fastjson2.JSON");
            fastjson2Present = true;
        } catch (Throwable e) {
            fastjson2Present = false;
        }

    }

    public static JsonFacade getDefaultJsonFacade() {
        if (defaultJsonFacade == null) {
            if (jacksonPresent) {
                usingJacksonAsDefaultJsonFacade();
            } else if (gsonPresent) {
                usingGsonAsDefaultJsonFacade();
            } else if (fastjson2Present) {
                usingFastjson2AsDefaultJsonFacade();
            } else {
                throw new JsonException("No supported JSON library found: please add Jackson/Gson/Fastjson2/... to the classpath");
            }
        }
        return defaultJsonFacade;
    }

    public static void usingJacksonAsDefaultJsonFacade() {
        defaultJsonFacade = new JacksonJsonFacade(new ObjectMapper());
    }

    public static void usingGsonAsDefaultJsonFacade() {
        defaultJsonFacade = new GsonJsonFacade(new Gson());
    }

    public static void usingFastjson2AsDefaultJsonFacade() {
        defaultJsonFacade = new Fastjson2JsonFacade();
    }

}
