package org.sjf4j.value;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.Sjf4j;
import org.sjf4j.exception.BindingException;
import org.sjf4j.meta.TypeRegistry;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Regression tests for field-declared codecs and the unified built-in registry. */
class AnnotatedValueCodecTest {

    static final class Key {
        final String text;
        Key(String text) { this.text = text; }
    }

    static ValueCodec<Key, String> codec(String suffix) {
        return new ValueCodec.SimpleValueCodec<>(Key.class, String.class,
                v -> v.text + suffix, raw -> new Key(raw + suffix));
    }

    public static final class KeyCodecs {
        // Deliberately named before the default to prove reflection order is irrelevant.
        @NodeValue(format = "expanded")
        public static final ValueCodec<Key, String> A_EXPANDED = codec("!");

        @NodeValue(format = "short", defaultCodec = true)
        public static final ValueCodec<Key, String> Z_DEFAULT = codec("");
    }

    @Test
    void discoversBuiltinsAndPreservesDefaultAndNamedFormats() {
        long annotated = Arrays.stream(ValueCodec.class.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(NodeValue.class)).count();
        assertEquals(23, annotated);

        assertEquals(String.class, TypeRegistry.requireRegisteredValueInfo(UUID.class, null).rawClazz);
        assertEquals("standard", TypeRegistry.requireRegisteredValueInfo(UUID.class, null).valueFormat);
        assertSame(ValueCodec.UUID_CODEC, TypeRegistry.requireRegisteredValueInfo(UUID.class, "standard").codec);
        assertThrows(BindingException.class,
                () -> TypeRegistry.requireRegisteredValueInfo(UUID.class, "canonical"));
        assertThrows(BindingException.class,
                () -> TypeRegistry.requireRegisteredValueInfo(UUID.class, ""));
        assertThrows(BindingException.class,
                () -> TypeRegistry.requireRegisteredValueInfo(LocalDate.class, ""));
        assertNotNull(TypeRegistry.requireRegisteredValueInfo(UUID.class, null).streamReader);
        assertNotNull(TypeRegistry.requireRegisteredValueInfo(UUID.class, null).streamWriter);
        assertEquals(String.class, TypeRegistry.requireRegisteredValueInfo(Instant.class, null).rawClazz);
        assertEquals("iso", TypeRegistry.requireRegisteredValueInfo(Instant.class, null).valueFormat);
        assertEquals("iso", TypeRegistry.requireRegisteredValueInfo(Instant.class, "iso").valueFormat);
        assertEquals("iso", ValueRegistry.resolve(Instant.class).stringInfos[0].valueFormat);
        assertEquals("epochMillis", ValueRegistry.resolve(Instant.class).numberInfos[0].valueFormat);
        assertEquals(Long.class, TypeRegistry.requireRegisteredValueInfo(Instant.class, "epochMillis").rawClazz);
        assertSame(PatternedValueCodec.LOCAL_DATE,
                TypeRegistry.requireRegisteredValueInfo(LocalDate.class, null).codec);
    }

    @Test
    void registersDefaultFirstEvenWhenNamedFieldSortsEarlier() {
        ValueRegistry.registerAnnotated(KeyCodecs.class);

        ValueSet resolved = ValueRegistry.resolve(Key.class);
        assertEquals(2, resolved.stringInfos.length);
        assertEquals("short", resolved.defaultInfo.valueFormat);
        assertEquals("expanded", TypeRegistry.requireRegisteredValueInfo(Key.class, "expanded").valueFormat);
        assertEquals("short", TypeRegistry.requireRegisteredValueInfo(Key.class, "short").valueFormat);
        Sjf4j json = Sjf4j.builder().build();
        assertEquals("abc", json.fromJson("\"abc\"", Key.class).text);
        assertEquals("\"abc\"", json.toJsonString(new Key("abc")));
        assertEquals("a", ((Key) resolved.defaultInfo.rawToValue("a")).text);
        assertEquals("a!", ((Key) TypeRegistry.requireRegisteredValueInfo(Key.class, "expanded")
                .rawToValue("a")).text);
    }

    static final class NamedOnly {
        final String value;
        NamedOnly(String value) { this.value = value; }
    }

    public static final class NamedOnlyCodecs {
        @NodeValue(format = "zzz")
        public static final ValueCodec<NamedOnly, String> B_ZZZ =
                new ValueCodec.SimpleValueCodec<>(NamedOnly.class, String.class,
                        x -> x.value, NamedOnly::new);

        @NodeValue(format = "aaa")
        public static final ValueCodec<NamedOnly, String> A_AAA =
                new ValueCodec.SimpleValueCodec<>(NamedOnly.class, String.class,
                        x -> x.value, NamedOnly::new);
    }

    public static final class NamedOnlyPreferred {
        @NodeValue(format = "preferred", defaultCodec = true)
        public static final ValueCodec<NamedOnly, String> PREFERRED =
                new ValueCodec.SimpleValueCodec<>(NamedOnly.class, String.class,
                        x -> x.value, NamedOnly::new);
    }

    @Test
    void namedOnlyFormatsHaveStableOrder() {
        ValueRegistry.registerAnnotated(NamedOnlyCodecs.class);
        assertEquals("aaa", ValueRegistry.resolve(NamedOnly.class).stringInfos[0].valueFormat);
        assertEquals("zzz", ValueRegistry.resolve(NamedOnly.class).stringInfos[1].valueFormat);
        ValueRegistry.registerAnnotated(NamedOnlyPreferred.class);
        assertEquals("preferred", ValueRegistry.resolve(NamedOnly.class).defaultInfo.valueFormat);
    }

    public static class NotStatic {
        @NodeValue public final ValueCodec<Key, String> BAD = codec("");
    }

    public static class NotFinal {
        @NodeValue public static ValueCodec<Key, String> BAD = codec("");
    }

    public static class NotPublic {
        @NodeValue private static final ValueCodec<Key, String> BAD = codec("");
    }

    public static class NotCodec {
        @NodeValue public static final String BAD = "bad";
    }

    public static class RawType {
        @SuppressWarnings("rawtypes")
        @NodeValue public static final ValueCodec BAD = codec("");
    }

    static final class Mismatched {
        final String value;
        Mismatched(String value) { this.value = value; }
    }

    public static class WrongDeclaredValueType {
        @SuppressWarnings({"unchecked", "rawtypes"})
        @NodeValue public static final ValueCodec<Mismatched, String> BAD =
                (ValueCodec) codec("");
    }

    public static class NullCodec {
        @NodeValue public static final ValueCodec<Mismatched, String> BAD = null;
    }

    public static class DuplicateDefault {
        @NodeValue(format = "first", defaultCodec = true)
        public static final ValueCodec<Mismatched, String> FIRST =
                new ValueCodec.SimpleValueCodec<>(Mismatched.class, String.class,
                        x -> x.value, Mismatched::new);
        @NodeValue(format = "second", defaultCodec = true)
        public static final ValueCodec<Mismatched, String> SECOND =
                new ValueCodec.SimpleValueCodec<>(Mismatched.class, String.class,
                        x -> x.value, Mismatched::new);
    }

    public static class DuplicateCodecInstance {
        private static final ValueCodec<Mismatched, String> SHARED =
                new ValueCodec.SimpleValueCodec<>(Mismatched.class, String.class,
                        x -> x.value, Mismatched::new);
        @NodeValue(format = "primary")
        public static final ValueCodec<Mismatched, String> FIRST = SHARED;
        @NodeValue(format = "alternative")
        public static final ValueCodec<Mismatched, String> SECOND = SHARED;
    }

    public static class DuplicateAcrossFields {
        @NodeValue(format = "same")
        public static final ValueCodec<Mismatched, String> FIRST =
                new ValueCodec.SimpleValueCodec<>(Mismatched.class, String.class,
                        x -> x.value, Mismatched::new);
        @NodeValue(format = "same")
        public static final ValueCodec<Mismatched, String> SECOND =
                new ValueCodec.SimpleValueCodec<>(Mismatched.class, String.class,
                        x -> x.value, Mismatched::new);
    }

    @Test
    void rejectsInvalidFieldDeclarations() {
        assertInvalid(NotStatic.class, "public static final");
        assertInvalid(NotFinal.class, "public static final");
        assertInvalid(NotPublic.class, "public static final");
        assertInvalid(NotCodec.class, "public static final");
        assertInvalid(RawType.class, "concrete V/R");
        assertInvalid(WrongDeclaredValueType.class, "do not match");
        assertInvalid(NullCodec.class, "is null");
        assertInvalid(DuplicateDefault.class, "multiple default");
        assertInvalid(DuplicateAcrossFields.class, "duplicate");
        assertInvalid(DuplicateCodecInstance.class, "same ValueCodec instance");
    }

    private static void assertInvalid(Class<?> owner, String part) {
        BindingException error = assertThrows(BindingException.class,
                () -> ValueRegistry.registerAnnotated(owner));
        assertTrue(error.getMessage().contains(part), error.getMessage());
    }
}
