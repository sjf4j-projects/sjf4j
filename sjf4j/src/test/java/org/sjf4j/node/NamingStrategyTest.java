package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.node.NodeBinding;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.path.JsonPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@Execution(ExecutionMode.SAME_THREAD)
class NamingStrategyTest {

    private final Sjf4j sjf4j = Sjf4j.builder()
            .jsonFacade(new SimpleJsonFacade())
            .build();

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
        SnakeUser user = sjf4j.fromJson("{\"user_name\":\"han\",\"login_count\":2,\"_internal_id\":\"x1\"}",
                SnakeUser.class);

        assertEquals("han", user.userName);
        assertEquals(2, user.loginCount);
        assertEquals("x1", user._internalId);
        assertEquals("han", JsonPath.compile("$.user_name").getNode(user));
        assertNull(JsonPath.compile("$.userName").getNode(user));
        assertEquals("{\"user_name\":\"han\",\"login_count\":2,\"_internal_id\":\"x1\"}",
                sjf4j.toJsonString(user));
    }

    @Test
    void testTypeLevelFieldBasedBindingOverridesBeanDefaults() {
        FieldBoundUser fieldUser = sjf4j.fromJson("{\"userName\":\"han\",\"loginCount\":3}",
                FieldBoundUser.class);
        BeanBoundUser beanUser = sjf4j.fromJson("{\"userName\":\"han\",\"loginCount\":3}",
                BeanBoundUser.class);

        assertEquals("han", fieldUser.userName);
        assertEquals(3, fieldUser.loginCount);
        assertEquals("{\"userName\":\"han\",\"loginCount\":3}", sjf4j.toJsonString(fieldUser));

        assertNull(beanUser.userName);
        assertEquals(0, beanUser.loginCount);
        assertEquals("{}", sjf4j.toJsonString(beanUser));
    }

    @Test
    void testTranslateDirectBranches() {
        assertSame(NamingStrategy.IDENTITY.translate(null), NamingStrategy.IDENTITY.translate(null));
        assertEquals("CamelCase", NamingStrategy.IDENTITY.translate("CamelCase"));
        assertNull(NamingStrategy.SNAKE_CASE.translate(null));
        assertEquals("metaplus_doc", NamingStrategy.SNAKE_CASE.translate("MetaplusDoc"));
    }

}
