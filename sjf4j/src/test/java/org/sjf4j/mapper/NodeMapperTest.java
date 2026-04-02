package org.sjf4j.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.JsonException;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NodeMapperTest {

    @Test
    public void testDefaultMappingAndOverrideOrder() {
        UserSource source = sampleUser();

        UserDtoJojo target = new NodeMapperBuilder<UserSource, UserDtoJojo>(UserSource.class, UserDtoJojo.class)
                .copy("displayName", "name")
                .value("displayName", "fixed")
                .compute("displayName", (root, parent, current) -> root.name + "!")
                .ensureCopy("$.meta.city", "profile.city")
                .ensureValue("$.meta.source", "sjf4j")
                .build()
                .map(source);

        assertEquals("Alice!", target.displayName);
        assertNotNull(target.meta);
        assertEquals("Shanghai", target.meta.city);
        assertEquals("sjf4j", target.getStringByPath("$.meta.source"));
        assertEquals("Shanghai", target.profile.city);
    }

    @Test
    public void testActionOrderFollowsDeclarationOrder() {
        UserSource source = sampleUser();

        UserDtoJojo target = new NodeMapperBuilder<UserSource, UserDtoJojo>(UserSource.class, UserDtoJojo.class)
                .compute("displayName", (root, parent, current) -> root.name + "!")
                .value("displayName", "fixed")
                .copy("displayName", "name")
                .build()
                .map(source);

        assertEquals("Alice", target.displayName);
    }

    @Test
    public void testNestedConverterAppliesToFieldAndListElements() {
        UserSource source = sampleUser();

        NodeMapper<OrderSource, OrderDtoJojo> orderConverter = new NodeMapperBuilder<OrderSource, OrderDtoJojo>(OrderSource.class, OrderDtoJojo.class)
                .compute("label", (order, parent, current) -> order.id + ":" + order.total)
                .build();

        UserDtoJojo target = new NodeMapperBuilder<UserSource, UserDtoJojo>(UserSource.class, UserDtoJojo.class)
                .with(orderConverter)
                .build()
                .map(source);

        assertEquals(2, target.orders.size());
        assertEquals("o-1:120", target.orders.get(0).label);
        assertEquals("o-2:80", target.orders.get(1).label);
        assertEquals("o-1:120", target.favoriteOrder.label);
    }

    @Test
    public void testComputeWithCurrentOnWildcardPath() {
        UserSource source = sampleUser();

        UserDtoJojo target = new NodeMapperBuilder<UserSource, UserDtoJojo>(UserSource.class, UserDtoJojo.class)
                .compute("friends[*].score", (UserSource root, Object parent, Object current) -> {
                    return ((Number) current).intValue() + 1;
                })
                .compute("friends[*].level", (UserSource root, Object parent, Object current) -> {
                    return root.name + ":" + ((JsonObject) parent).getString("name");
                })
                .build()
                .map(source);

        assertEquals(96, target.friends.get(0).score);
        assertEquals(61, target.friends.get(1).score);
        assertEquals("Alice:Bob", target.friends.get(0).level);
        assertEquals("Alice:Carol", target.friends.get(1).level);
    }

    @Test
    public void testPojoTargetAndCollectionProjection() {
        UserSource source = sampleUser();

        NodeMapper<OrderSource, OrderDtoPojo> orderConverter = new NodeMapperBuilder<OrderSource, OrderDtoPojo>(OrderSource.class, OrderDtoPojo.class)
                .compute("label", (order, parent, current) -> order.id + ":" + order.total)
                .build();

        UserDtoPojo target = new NodeMapperBuilder<UserSource, UserDtoPojo>(UserSource.class, UserDtoPojo.class)
                .with(orderConverter)
                .copy("displayName", "name")
                .build()
                .map(source);

        assertEquals("Alice", target.displayName);
        assertEquals("Shanghai", target.profile.city);
        assertEquals("o-1:120", target.orders.get(0).label);
        assertEquals("o-1:120", target.favoriteOrder.label);
    }

    @Test
    public void testEnsureActionsCreateMissingTargetPath() {
        UserSource source = sampleUser();

        UserDtoJojo target = new NodeMapperBuilder<UserSource, UserDtoJojo>(UserSource.class, UserDtoJojo.class)
                .ensureCopy("$.meta.city", "profile.city")
                .ensureValue("$.meta.source", "sjf4j")
                .ensureCompute("$.meta.label", (root, parent, current) -> root.name + ":ok")
                .build()
                .map(source);

        assertNotNull(target.meta);
        assertEquals("Shanghai", target.meta.city);
        assertEquals("sjf4j", target.getStringByPath("$.meta.source"));
        assertEquals("Alice:ok", target.getStringByPath("$.meta.label"));
    }

    @Test
    public void testNonEnsureActionsDoNotCreateMissingTargetPath() {
        UserSource source = sampleUser();

        assertThrows(JsonException.class, () -> new NodeMapperBuilder<UserSource, UserDtoJojo>(UserSource.class, UserDtoJojo.class)
                .copy("$.meta.city", "profile.city")
                .build()
                .map(source));

        assertThrows(JsonException.class, () -> new NodeMapperBuilder<UserSource, UserDtoJojo>(UserSource.class, UserDtoJojo.class)
                .value("$.meta.source", "sjf4j")
                .build()
                .map(source));

        assertThrows(JsonException.class, () -> new NodeMapperBuilder<UserSource, UserDtoJojo>(UserSource.class, UserDtoJojo.class)
                .compute("$.meta.label", (root, parent, current) -> root.name + ":x")
                .build()
                .map(source));
    }

    @Test
    public void testCopyRejectsMultiSourcePath() {
        assertThrows(JsonException.class, () -> new NodeMapperBuilder<UserSource, UserDtoJojo>(UserSource.class, UserDtoJojo.class)
                .copy("displayName", "friends[*].name"));
        assertThrows(JsonException.class, () -> new NodeMapperBuilder<UserSource, UserDtoJojo>(UserSource.class, UserDtoJojo.class)
                .copy("friends[*].level", "friends[*].name"));
        assertThrows(JsonException.class, () -> new NodeMapperBuilder<UserSource, UserDtoJojo>(UserSource.class, UserDtoJojo.class)
                .copy("friends[*].level", "name"));
    }

    @Test
    public void testValueRejectsMultiTargetPath() {
        assertThrows(JsonException.class, () -> new NodeMapperBuilder<UserSource, UserDtoJojo>(UserSource.class, UserDtoJojo.class)
                .value("friends[*].level", "A"));
    }

    @Test
    public void testEnsureValueRejectsMultiTargetPath() {
        assertThrows(JsonException.class, () -> new NodeMapperBuilder<UserSource, UserDtoJojo>(UserSource.class, UserDtoJojo.class)
                .ensureValue("friends[*].level", "A"));
    }

    public static class UserSource {
        public String name;
        public ProfileSource profile;
        public List<OrderSource> orders;
        public OrderSource favoriteOrder;
        public List<FriendSource> friends;
    }

    public static class ProfileSource {
        public String city;
    }

    public static class OrderSource {
        public String id;
        public int total;
    }

    public static class FriendSource {
        public String name;
        public int score;
    }

    public static class UserDtoPojo {
        public String name;
        public String displayName;
        public ProfileDtoPojo profile;
        public List<OrderDtoPojo> orders;
        public OrderDtoPojo favoriteOrder;
    }

    public static class ProfileDtoPojo {
        public String city;
    }

    public static class OrderDtoPojo {
        public String id;
        public int total;
        public String label;
    }

    public static class UserDtoJojo extends JsonObject {
        public String name;
        public String displayName;
        public ProfileDtoJojo profile;
        public List<OrderDtoJojo> orders;
        public OrderDtoJojo favoriteOrder;
        public List<FriendDtoJojo> friends;
        public MetaJojo meta;
    }

    public static class ProfileDtoJojo extends JsonObject {
        public String city;
    }

    public static class OrderDtoJojo extends JsonObject {
        public String id;
        public int total;
        public String label;
    }

    public static class FriendDtoJojo extends JsonObject {
        public String name;
        public int score;
        public String level;
    }

    public static class MetaJojo extends JsonObject {
        public String city;
    }

    private static UserSource sampleUser() {
        UserSource source = new UserSource();
        source.name = "Alice";
        source.profile = new ProfileSource();
        source.profile.city = "Shanghai";

        OrderSource order1 = new OrderSource();
        order1.id = "o-1";
        order1.total = 120;
        OrderSource order2 = new OrderSource();
        order2.id = "o-2";
        order2.total = 80;
        source.orders = Arrays.asList(order1, order2);
        source.favoriteOrder = order1;

        FriendSource friend1 = new FriendSource();
        friend1.name = "Bob";
        friend1.score = 95;
        FriendSource friend2 = new FriendSource();
        friend2.name = "Carol";
        friend2.score = 60;
        source.friends = Arrays.asList(friend1, friend2);
        return source;
    }
}
