package org.sjf4j.node;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.annotation.node.NodeBinding;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.path.JsonPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @NodeBinding(naming = NamingStrategy.SNAKE_CASE)
    static class SnakeUser extends JsonObject {
        public String userName;
        public int loginCount;
        public String _internalId;
    }

    @NodeBinding(access = AccessStrategy.FIELD_BASED)
    static class FieldBoundUser {
        private String userName;
        private int loginCount;
    }

    static class BeanBoundUser {
        private String userName;
        private int loginCount;
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
    void testTypeLevelFieldBasedBindingOverridesBeanDefaults() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(previousConfig)
                .jsonFacade(new SimpleJsonFacade())
                .build());

        FieldBoundUser fieldUser = Sjf4j.fromJson("{\"userName\":\"han\",\"loginCount\":3}",
                FieldBoundUser.class);
        BeanBoundUser beanUser = Sjf4j.fromJson("{\"userName\":\"han\",\"loginCount\":3}",
                BeanBoundUser.class);

        assertEquals("han", fieldUser.userName);
        assertEquals(3, fieldUser.loginCount);
        assertEquals("{\"userName\":\"han\",\"loginCount\":3}", Sjf4j.toJsonString(fieldUser));

        assertNull(beanUser.userName);
        assertEquals(0, beanUser.loginCount);
        assertEquals("{}", Sjf4j.toJsonString(beanUser));
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
