package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adjacent multi-source constructor reads can share a null check without
 * moving getters or changing custom converter null semantics.
 */
class CompiledMapperAdjacentReadTest {

    @Test
    void adjacentDirectReadsShareGuardWithoutChangingAccessorOrConverterOrder() throws Exception {
        NavigatorTestCompiler.Result compiled = compileFixture();
        assertTrue(compiled.success, compiled.diagnostics());

        String generated = compiled.generatedSource("fixture/Mapper_Impl.java");
        String body = methodBody(generated, "CompositeTarget nested(");
        assertEquals(1, count(body, "if (account != null)"), body);
        assertEquals(2, count(body, "if (profile != null)"), body);
        assertTrue(body.contains("account.tier()"), body);
        assertTrue(body.contains("account.active()"), body);
        assertTrue(body.contains("contact("), body);

        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class<?> profileType = loader.loadClass("fixture.Profile");
            Class<?> accountType = loader.loadClass("fixture.Account");
            Class<?> contactType = loader.loadClass("fixture.Contact");
            Class<?> contactDtoType = loader.loadClass("fixture.ContactDto");
            Class<?> targetType = loader.loadClass("fixture.CompositeTarget");
            Class<?> mapperType = loader.loadClass("fixture.Mapper_Impl");
            Class<?> trace = loader.loadClass("fixture.Trace");

            @SuppressWarnings("unchecked")
            List<String> events = (List<String>) trace.getField("EVENTS").get(null);
            Object mapper = mapperType.getConstructor().newInstance();
            Object contact = contactType.getConstructor(String.class).newInstance("Paris");
            Object profile = profileType.getConstructor(String.class, contactType)
                    .newInstance("Grace", contact);
            Object account = accountType.getConstructor(String.class, boolean.class)
                    .newInstance("gold", true);
            Constructor<?> targetCtor = targetType.getConstructor(String.class, String.class,
                    boolean.class, contactDtoType);
            Constructor<?> dtoCtor = contactDtoType.getConstructor(String.class);
            Method nested = mapperType.getMethod("nested", profileType, accountType);

            events.clear();
            Object mapped = nested.invoke(mapper, profile, account);
            assertEquals(targetCtor.newInstance("Grace", "gold", true,
                    dtoCtor.newInstance("Paris")), mapped);
            assertEquals(Arrays.asList("profile.name", "account.tier",
                    "account.active", "profile.contact", "contact.convert"), events);

            events.clear();
            Object absentAccount = nested.invoke(mapper, profile, null);
            assertEquals(targetCtor.newInstance("Grace", null, false,
                    dtoCtor.newInstance("Paris")), absentAccount);
            assertEquals(Arrays.asList("profile.name", "profile.contact",
                    "contact.convert"), events);

            events.clear();
            Object absentProfile = nested.invoke(mapper, null, account);
            assertEquals(targetCtor.newInstance(null, "gold", true,
                    dtoCtor.newInstance("NULL")), absentProfile);
            assertEquals(Arrays.asList("account.tier", "account.active",
                    "contact.convert"), events);

            events.clear();
            assertNull(nested.invoke(mapper, new Object[]{null, null}));
            assertTrue(events.isEmpty());
        }
    }

    @Test
    void primitiveToBoxedConstructorArgumentsRetainMissingSourceNull() throws Exception {
        NavigatorTestCompiler.Result compiled = compileFixture();
        assertTrue(compiled.success, compiled.diagnostics());

        String body = methodBody(compiled.generatedSource("fixture/Mapper_Impl.java"),
                "BoxedTarget boxed(");
        assertEquals(1, count(body, "if (account != null)"), body);

        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class<?> profileType = loader.loadClass("fixture.Profile");
            Class<?> accountType = loader.loadClass("fixture.Account");
            Class<?> contactType = loader.loadClass("fixture.Contact");
            Class<?> boxedTargetType = loader.loadClass("fixture.BoxedTarget");
            Class<?> mapperType = loader.loadClass("fixture.Mapper_Impl");
            Object mapper = mapperType.getConstructor().newInstance();
            Object profile = profileType.getConstructor(String.class, contactType)
                    .newInstance("Grace", null);
            Object account = accountType.getConstructor(String.class, boolean.class)
                    .newInstance("gold", true);
            Method boxed = mapperType.getMethod("boxed", profileType, accountType);
            Constructor<?> ctor = boxedTargetType.getConstructor(String.class, String.class,
                    Boolean.class);
            assertEquals(ctor.newInstance("Grace", null, null),
                    boxed.invoke(mapper, profile, null));
            assertEquals(ctor.newInstance("Grace", "gold", true),
                    boxed.invoke(mapper, profile, account));
        }
    }

    private static String methodBody(String generated, String signature) {
        int start = generated.indexOf(signature);
        assertTrue(start >= 0, generated);
        int end = generated.indexOf("\n    }", start);
        assertTrue(end > start, generated);
        return generated.substring(start, end);
    }

    private static int count(String value, String pattern) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(pattern, at)) != -1; at += pattern.length()) {
            count++;
        }
        return count;
    }

    private static NavigatorTestCompiler.Result compileFixture() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Trace.java",
                "package fixture; public final class Trace { "
                + "public static final java.util.List<String> EVENTS = new java.util.ArrayList<>(); }");
        sources.put("fixture/Contact.java",
                "package fixture; public record Contact(String city) {}");
        sources.put("fixture/ContactDto.java",
                "package fixture; public record ContactDto(String city) {}");
        sources.put("fixture/Profile.java",
                "package fixture; public record Profile(String name, Contact contact) { "
                + "public String name() { Trace.EVENTS.add(\"profile.name\"); return name; } "
                + "public Contact contact() { Trace.EVENTS.add(\"profile.contact\"); return contact; } "
                + "}");
        sources.put("fixture/Account.java",
                "package fixture; public record Account(String tier, boolean active) { "
                + "public String tier() { Trace.EVENTS.add(\"account.tier\"); return tier; } "
                + "public boolean active() { Trace.EVENTS.add(\"account.active\"); return active; } "
                + "}");
        sources.put("fixture/CompositeTarget.java",
                "package fixture; public record CompositeTarget(String name, String tier, "
                + "boolean active, ContactDto contact) {}");
        sources.put("fixture/BoxedTarget.java",
                "package fixture; public record BoxedTarget(String name, String tier, Boolean active) {}");
        sources.put("fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.*; "
                + "@CompiledMapper public interface Mapper { "
                + "@MappingOptions(methods={\"this::contact\"}) "
                + "@Mapping(target=\"name\",source=\"profile:name\") "
                + "@Mapping(target=\"tier\",source=\"account:tier\") "
                + "@Mapping(target=\"active\",source=\"account:active\") "
                + "@Mapping(target=\"contact\",source=\"profile:contact\") "
                + "CompositeTarget nested(Profile profile,Account account); "
                + "@Mapping(target=\"name\",source=\"profile:name\") "
                + "@Mapping(target=\"tier\",source=\"account:tier\") "
                + "@Mapping(target=\"active\",source=\"account:active\") "
                + "BoxedTarget boxed(Profile profile,Account account); "
                + "default ContactDto contact(Contact source) { "
                + "Trace.EVENTS.add(\"contact.convert\"); "
                + "return new ContactDto(source == null ? \"NULL\" : source.city()); } "
                + "}");
        return NavigatorTestCompiler.compile(sources, CodegenProcessor.class,
                "-Xlint:cast", "-Xlint:unchecked", "-Werror");
    }
}
