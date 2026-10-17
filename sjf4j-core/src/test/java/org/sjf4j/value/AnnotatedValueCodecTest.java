package org.sjf4j.value;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeValue;
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
        @NodeValue(formats = {"expanded"})
        public static final ValueCodec<Key, String> A_EXPANDED = codec("!");

        @NodeValue(formats = {"", "short"})
        public static final ValueCodec<Key, String> Z_DEFAULT = codec("");
    }

    @Test
    void discoversBuiltinsAndPreservesDefaultAndNamedFormats() {
        long annotated = Arrays.stream(ValueCodec.class.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(NodeValue.class)).count();
        assertEquals(22, annotated);

        assertEquals(String.class, TypeRegistry.requireRegisteredValueInfo(UUID.class, null).rawClazz);
        assertEquals(String.class, TypeRegistry.requireRegisteredValueInfo(Instant.class, null).rawClazz);
        assertEquals("", TypeRegistry.requireRegisteredValueInfo(Instant.class, null).valueFormat);
        assertEquals("iso", TypeRegistry.requireRegisteredValueInfo(Instant.class, "iso").valueFormat);
        assertEquals(Long.class, TypeRegistry.requireRegisteredValueInfo(Instant.class, "epochMillis").rawClazz);
        assertSame(PatternedValueCodec.LOCAL_DATE,
                TypeRegistry.requireRegisteredValueInfo(LocalDate.class, null).codec);
    }

    @Test
    void registersDefaultFirstEvenWhenNamedFieldSortsEarlier() {
        ValueRegistry.registerAnnotated(KeyCodecs.class);

        ValueInfo[] resolved = ValueRegistry.resolve(Key.class);
        assertEquals(3, resolved.length);
        assertEquals("", resolved[0].valueFormat);
        assertEquals("expanded", TypeRegistry.requireRegisteredValueInfo(Key.class, "expanded").valueFormat);
        assertEquals("short", TypeRegistry.requireRegisteredValueInfo(Key.class, "short").valueFormat);
        assertEquals("a", ((Key) resolved[0].rawToValue("a")).text);
        assertEquals("a!", ((Key) TypeRegistry.requireRegisteredValueInfo(Key.class, "expanded")
                .rawToValue("a")).text);
    }

    static final class NamedOnly {
        final String value;
        NamedOnly(String value) { this.value = value; }
    }

    public static final class NamedOnlyCodecs {
        @NodeValue(formats = {"zzz"})
        public static final ValueCodec<NamedOnly, String> B_ZZZ =
                new ValueCodec.SimpleValueCodec<>(NamedOnly.class, String.class,
                        x -> x.value, NamedOnly::new);

        @NodeValue(formats = {"aaa"})
        public static final ValueCodec<NamedOnly, String> A_AAA =
                new ValueCodec.SimpleValueCodec<>(NamedOnly.class, String.class,
                        x -> x.value, NamedOnly::new);
    }

    @Test
    void namedOnlyFormatsHaveStableOrder() {
        ValueRegistry.registerAnnotated(NamedOnlyCodecs.class);
        assertEquals("aaa", ValueRegistry.resolve(NamedOnly.class)[0].valueFormat);
        assertEquals("zzz", ValueRegistry.resolve(NamedOnly.class)[1].valueFormat);
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

    public static class EmptyFormats {
        @NodeValue(formats = {})
        public static final ValueCodec<Mismatched, String> BAD =
                new ValueCodec.SimpleValueCodec<>(Mismatched.class, String.class,
                        x -> x.value, Mismatched::new);
    }

    public static class DuplicateFormat {
        @NodeValue(formats = {"iso", "iso"})
        public static final ValueCodec<Mismatched, String> BAD =
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
        assertInvalid(EmptyFormats.class, "no formats");
        assertInvalid(DuplicateFormat.class, "duplicate");
    }

    private static void assertInvalid(Class<?> owner, String part) {
        BindingException error = assertThrows(BindingException.class,
                () -> ValueRegistry.registerAnnotated(owner));
        assertTrue(error.getMessage().contains(part), error.getMessage());
    }
}
