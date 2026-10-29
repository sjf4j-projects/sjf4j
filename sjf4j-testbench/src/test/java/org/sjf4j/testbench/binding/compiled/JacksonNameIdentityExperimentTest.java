package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.core.JsonFactory;
import org.junit.jupiter.api.Test;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import tools.jackson.core.json.JsonFactoryBuilder;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class JacksonNameIdentityExperimentTest {
    private static final String JSON = "[{\"repeated_field_98765\":1,\"other_field_98765\":2},{\"repeated_field_98765\":3,\"other_field_98765\":4}]";
    private static String key(Object listObj, int index) {
        Map<?, ?> map = (Map<?, ?>) ((List<?>) listObj).get(index);
        return (String) map.keySet().iterator().next();
    }
    @Test
    void jackson2() {
        Jackson2Binder binder = new Jackson2Binder();
        Object a = binder.readNode(JSON, List.class);
        Object b = binder.readNode(JSON, List.class);
        String k1 = key(a, 0), k2 = key(a, 1), k3 = key(b, 0);
        String k4 = key(new Jackson2Binder().readNode(JSON, List.class), 0);
        System.out.printf("JACKSON2 sameParser=%s sameBinderAcrossParsers=%s differentFactory=%s identities=%d,%d,%d,%d%n",
                k1 == k2, k1 == k3, k1 == k4,
                System.identityHashCode(k1),System.identityHashCode(k2),System.identityHashCode(k3),System.identityHashCode(k4));
        assertSame(k1, k2);
        assertSame(k1, k3);
    }
    @Test
    void jackson3() {
        Jackson3Binder binder = new Jackson3Binder();
        Object a = binder.readNode(JSON, List.class);
        Object b = binder.readNode(JSON, List.class);
        String k1 = key(a, 0), k2 = key(a, 1), k3 = key(b, 0);
        String k4 = key(new Jackson3Binder().readNode(JSON, List.class), 0);
        System.out.printf("JACKSON3 sameParser=%s sameBinderAcrossParsers=%s differentFactory=%s identities=%d,%d,%d,%d%n",
                k1 == k2, k1 == k3, k1 == k4,
                System.identityHashCode(k1),System.identityHashCode(k2),System.identityHashCode(k3),System.identityHashCode(k4));
        assertSame(k1, k2);
        assertSame(k1, k3);
    }
}
