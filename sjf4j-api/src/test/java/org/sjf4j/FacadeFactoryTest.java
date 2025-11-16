package org.sjf4j;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.facades.JsonFacade;
import org.sjf4j.facades.YamlFacade;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class FacadeFactoryTest {

    @Test
    public void testGetDefaultJsonFacade() {
        // 应该能获取到默认的JsonFacade
        JsonFacade<?, ?> facade = FacadeFactory.getDefaultJsonFacade();
        assertNotNull(facade);
        
        // 测试能够正常使用
        JsonObject jo = JsonObject.fromJson("{\"test\":123}");
        assertNotNull(jo);
        assertEquals(123, jo.getInteger("test"));
    }

    @Test
    public void testUsingJacksonAsDefault() {
        FacadeFactory.usingJacksonAsDefault();
        JsonFacade<?, ?> facade = FacadeFactory.getDefaultJsonFacade();
        assertNotNull(facade);
        
        JsonObject jo = JsonObject.fromJson("{\"key\":\"value\"}");
        assertEquals("value", jo.getString("key"));
    }

    @Test
    public void testUsingGsonAsDefault() {
        FacadeFactory.usingGsonAsDefault();
        JsonFacade<?, ?> facade = FacadeFactory.getDefaultJsonFacade();
        assertNotNull(facade);
        
        JsonObject jo = JsonObject.fromJson("{\"key\":\"value\"}");
        assertEquals("value", jo.getString("key"));
    }

    @Test
    public void testUsingFastjson2AsDefault() {
        FacadeFactory.usingFastjson2AsDefault();
        JsonFacade<?, ?> facade = FacadeFactory.getDefaultJsonFacade();
        assertNotNull(facade);
        
        JsonObject jo = JsonObject.fromJson("{\"key\":\"value\"}");
        assertEquals("value", jo.getString("key"));
    }

    @Test
    public void testGetDefaultYamlFacade() {
        try {
            YamlFacade<?, ?> facade = FacadeFactory.getDefaultYamlFacade();
            assertNotNull(facade);
            
            // 测试YAML功能
            JsonObject jo = new JsonObject("key", "value");
            String yaml = jo.toYaml();
            assertNotNull(yaml);
            
            JsonObject fromYaml = JsonObject.fromYaml(yaml);
            assertEquals(jo, fromYaml);
        } catch (JsonException e) {
            // 如果没有YAML库，这是预期的
            log.info("YAML facade not available: {}", e.getMessage());
        }
    }

    @Test
    public void testUsingSnakeAsDefault() {
        try {
            FacadeFactory.usingSnakeAsDefault();
            YamlFacade<?, ?> facade = FacadeFactory.getDefaultYamlFacade();
            assertNotNull(facade);
        } catch (JsonException e) {
            // 如果没有SnakeYAML库，这是预期的
            log.info("SnakeYAML facade not available: {}", e.getMessage());
        }
    }

    @Test
    public void testSetDefaultJsonFacade() {
        // 保存原始facade
        JsonFacade<?, ?> original = FacadeFactory.getDefaultJsonFacade();
        
        try {
            // 设置新的facade
            FacadeFactory.setDefaultJsonFacade(original);
            JsonFacade<?, ?> facade = FacadeFactory.getDefaultJsonFacade();
            assertEquals(original, facade);
        } finally {
            // 恢复原始facade
            FacadeFactory.setDefaultJsonFacade(original);
        }
    }

    @Test
    public void testSetDefaultYamlFacade() {
        try {
            YamlFacade<?, ?> original = FacadeFactory.getDefaultYamlFacade();
            
            // 设置新的facade
            FacadeFactory.setDefaultYamlFacade(original);
            YamlFacade<?, ?> facade = FacadeFactory.getDefaultYamlFacade();
            assertEquals(original, facade);
        } catch (JsonException e) {
            // 如果没有YAML库，这是预期的
            log.info("YAML facade not available: {}", e.getMessage());
        }
    }

    @Test
    public void testFacadeConsistency() {
        // 测试不同facade都能正确工作
        JsonObject testData = new JsonObject("a", 1, "b", "test", "c", true);
        
        // 测试Jackson
        FacadeFactory.usingJacksonAsDefault();
        String json1 = testData.toJson();
        JsonObject from1 = JsonObject.fromJson(json1);
        assertEquals(testData, from1);
        
        // 测试Gson
        FacadeFactory.usingGsonAsDefault();
        String json2 = testData.toJson();
        JsonObject from2 = JsonObject.fromJson(json2);
        assertEquals(testData, from2);
        
        // 测试Fastjson2
        FacadeFactory.usingFastjson2AsDefault();
        String json3 = testData.toJson();
        JsonObject from3 = JsonObject.fromJson(json3);
        assertEquals(testData, from3);
    }

}

