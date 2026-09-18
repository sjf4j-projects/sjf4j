package org.sjf4j.binding.contract;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** SJF4J-supported JDK string-like bindings and explicit unsupported-type contracts. */
public abstract class JDKStringLikeTypeDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);
    private Object read(String json, Class<?> type) { return binding(StreamingContext.EMPTY).readNode(json, type); }

    /** Source: JDKStringLikeTypeDeserTest#testCharset. */
    @Test void testCharset() { assertSame(StandardCharsets.UTF_8, read("\"UTF-8\"", java.nio.charset.Charset.class)); }
    /** Source: JDKStringLikeTypeDeserTest#testCurrency. */
    @Test void testCurrency() { assertEquals(Currency.getInstance("USD"), read("\"USD\"", Currency.class)); }
    /** Source: JDKStringLikeTypeDeserTest#testFile. */
    @Test void testFile() { assertEquals(new File("/tmp/sjf4j").getAbsoluteFile(), read("\"/tmp/sjf4j\"", File.class)); }
    /** Source: JDKStringLikeTypeDeserTest#testCharSequence. */
    @Test void testCharSequenceIsRejectedWithoutCodec() { assertThrows(RuntimeException.class, () -> read("\"abc\"", CharSequence.class)); }
    /** Source: JDKStringLikeTypeDeserTest#testPattern. */
    @Test void testPattern() { assertEquals("abc:\\s?(\\d+)", ((Pattern) read("\"abc:\\\\s?(\\\\d+)\"", Pattern.class)).pattern()); }
    /** Source: JDKStringLikeTypeDeserTest#testStringBuilder. */
    @Test void testStringBuilderIsRejectedWithoutCodec() { assertThrows(RuntimeException.class, () -> read("\"abc\"", StringBuilder.class)); }
    /** Source: JDKStringLikeTypeDeserTest#testStringBuffer. */
    @Test void testStringBufferIsRejectedWithoutCodec() { assertThrows(RuntimeException.class, () -> read("\"abc\"", StringBuffer.class)); }
    /** Source: JDKStringLikeTypeDeserTest#testURI. */
    @Test void testURI() { assertEquals(URI.create("http://foo.com"), read("\"http://foo.com\"", URI.class)); }
    /** Source: JDKStringLikeTypeDeserTest#testURL. */
    @Test void testURL() throws Exception { assertEquals(new URL("http://foo.com"), read("\"http://foo.com\"", URL.class)); }
    /** Source: UUIDDeserializationTest#testUUID. */
    @Test void testUUID() { assertEquals(UUID.fromString("76e6d183-5f68-4afa-b94a-922c1fdb83f8"), read("\"76e6d183-5f68-4afa-b94a-922c1fdb83f8\"", UUID.class)); }
    /** Source: LocaleDeserializationTest#testLocale. */
    @Test void testLocale() { assertEquals(new Locale("en"), read("\"en\"", Locale.class)); }
    /** Source: JDK7TypesTest#testPathRoundTrip. */
    @Test void testPath() { assertEquals(Path.of("/tmp", "sjf4j").toAbsolutePath(), ((Path) read("\"/tmp/sjf4j\"", Path.class)).toAbsolutePath()); }
}
