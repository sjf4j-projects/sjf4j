package org.sjf4j.node;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.annotation.node.NodeNaming;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.facade.jackson2.Jackson2JsonFacade;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.path.JsonPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@Execution(ExecutionMode.SAME_THREAD)
class NamingStrategyTest {

    private Sjf4jConfig previousConfig;

    @BeforeEach
    void saveGlobalConfig() {
        previousConfig = Sjf4jConfig.global();
    }

    @AfterEach
    void restoreGlobalConfig() {
        if (previousConfig != null) {
            Sjf4jConfig.global(previousConfig);
        }
    }

    @NodeNaming(NamingStrategy.SNAKE_CASE)
    static class SnakeUser extends JsonObject {
        public String userName;
        public int loginCount;
        public String _internalId;
    }

    static class GlobalUser extends JsonObject {
        public String userName;
        public int loginCount;
    }

    @Test
    void testTypeLevelSnakeCaseBindingAndJsonPath() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(previousConfig)
                .jsonFacade(new SimpleJsonFacade())
                .namingStrategy(NamingStrategy.IDENTITY)
                .build());

        SnakeUser user = Sjf4j.fromJson("{\"user_name\":\"han\",\"login_count\":2,\"_internal_id\":\"x1\"}",
                SnakeUser.class);

        assertEquals("han", user.userName);
        assertEquals(2, user.loginCount);
        assertEquals("x1", user._internalId);
        assertEquals("han", JsonPath.compile("$.user_name").getNode(user));
        assertNull(JsonPath.compile("$.userName").getNode(user));
        assertEquals("{\"user_name\":\"han\",\"login_count\":2,\"_internal_id\":\"x1\"}",
                Sjf4j.toJsonString(user));
    }

    @Test
    void testGlobalSnakeCaseRecreatesCopiedJsonFacade() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(previousConfig)
                .jsonFacade(new Jackson2JsonFacade(StreamingFacade.StreamingMode.PLUGIN_MODULE))
                .build());
        Object firstFacade = Sjf4jConfig.global().getJsonFacade();

        Sjf4jConfig.global(new Sjf4jConfig.Builder(Sjf4jConfig.global())
                .namingStrategy(NamingStrategy.SNAKE_CASE)
                .build());
        Object secondFacade = Sjf4jConfig.global().getJsonFacade();

        assertNotSame(firstFacade, secondFacade);

        GlobalUser user = Sjf4j.fromJson("{\"user_name\":\"han\",\"login_count\":3}", GlobalUser.class);
        assertEquals("han", user.userName);
        assertEquals(3, user.loginCount);
        assertEquals("{\"user_name\":\"han\",\"login_count\":3}", Sjf4j.toJsonString(user));
    }

    @Test
    void testTranslateDirectBranches() {
        assertSame(NamingStrategy.IDENTITY.translate(null), NamingStrategy.IDENTITY.translate(null));
        assertEquals("CamelCase", NamingStrategy.IDENTITY.translate("CamelCase"));
        assertNull(NamingStrategy.SNAKE_CASE.translate(null));
        assertEquals("", NamingStrategy.SNAKE_CASE.translate(""));
        assertEquals("___", NamingStrategy.SNAKE_CASE.translate("___"));
        assertEquals("already_snake", NamingStrategy.SNAKE_CASE.translate("already_snake"));
        assertEquals("user_name", NamingStrategy.SNAKE_CASE.translate("UserName"));
        assertEquals("url", NamingStrategy.SNAKE_CASE.translate("URL"));
        assertEquals("user_name", NamingStrategy.SNAKE_CASE.translate("userName"));
        assertEquals("url_value", NamingStrategy.SNAKE_CASE.translate("URLValue"));
        assertEquals("version2_value", NamingStrategy.SNAKE_CASE.translate("version2Value"));
        assertEquals("__internal_id", NamingStrategy.SNAKE_CASE.translate("__internalId"));
    }

}
