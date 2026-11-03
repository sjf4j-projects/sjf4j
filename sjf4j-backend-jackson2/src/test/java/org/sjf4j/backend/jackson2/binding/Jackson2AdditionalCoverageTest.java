package org.sjf4j.backend.jackson2.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.StreamingParser;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Jackson2AdditionalCoverageTest {

    @Test
    void readsTemporalStringsNullsAndRejectsWrongToken() throws Exception {
        String json = "[\"12:34:56.123\",null,42,\"2026-11-03T12:34:56\",null,42,"
                + "\"2026-11-03T12:34:56Z\",null,42,"
                + "\"2026-11-03T12:34:56+02:00\",null,42,"
                + "\"12:34:56+02:00\",null,42]";
        Jackson2Binder binder = new Jackson2Binder();
        for (boolean utf8 : new boolean[]{false, true}) {
            try (Jackson2Parser parser = utf8
                    ? binder.createParser(json.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    : binder.createParser(json)) {
                parser.readArrayStart();
                assertEquals(LocalTime.parse("12:34:56.123"), parser.readLocalTime());
                assertNull(parser.readLocalTime());
                assertThrows(IOException.class, parser::readLocalTime);

                assertEquals(LocalDateTime.parse("2026-11-03T12:34:56"), parser.readLocalDateTime());
                assertNull(parser.readLocalDateTime());
                assertThrows(IOException.class, parser::readLocalDateTime);

                assertEquals(Instant.parse("2026-11-03T12:34:56Z"), parser.readInstant());
                assertNull(parser.readInstant());
                assertThrows(IOException.class, parser::readInstant);

                assertEquals(OffsetDateTime.parse("2026-11-03T12:34:56+02:00"), parser.readOffsetDateTime());
                assertNull(parser.readOffsetDateTime());
                assertThrows(IOException.class, parser::readOffsetDateTime);

                assertEquals(OffsetTime.parse("12:34:56+02:00"), parser.readOffsetTime());
                assertNull(parser.readOffsetTime());
                assertThrows(IOException.class, parser::readOffsetTime);
                parser.readArrayEnd();
                parser.end();
            }
        }
    }

    @Test
    void boxedNumbersHandleZeroNullNonzeroAndPrefetchedValues() throws Exception {
        try (Jackson2Parser parser = new Jackson2Binder().createParser(
                "[0,null,7,1.9,0,null,9000000000,1.9,true,null,false,"
                        + "null,2.5,null,12345678901234567890,null,1.20]")) {
            parser.readArrayStart();
            assertEquals(0, parser.readInt());
            assertNull(parser.readInt());
            assertEquals(7, parser.readInt());
            assertEquals(1, parser.readInt());
            assertEquals(0L, parser.readLong());
            assertNull(parser.readLong());
            assertEquals(9000000000L, parser.readLong());
            assertEquals(1L, parser.readLong());
            assertEquals(true, parser.readBoolean());
            assertNull(parser.readBoolean());
            assertEquals(StreamingParser.Token.BOOLEAN, parser.peekToken());
            assertEquals(false, parser.readBoolean());
            assertNull(parser.readNumber());
            assertEquals(2.5, parser.readNumber().doubleValue());
            assertNull(parser.readBigInteger());
            assertEquals(new BigInteger("12345678901234567890"), parser.readBigInteger());
            assertNull(parser.readBigDecimal());
            assertEquals(new BigDecimal("1.20"), parser.readBigDecimal());
            parser.readArrayEnd();
            parser.end();
        }
    }

    @Test
    void writesNumericSubtypesAndNullableUuid() throws Exception {
        StringWriter output = new StringWriter();
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        try (Jackson2Generator generator = new Jackson2Binder().createGenerator(output)) {
            generator.writeArrayStart();
            generator.writeNumberValue(7L);
            generator.writeNumberValue(2.5d);
            generator.writeNumberValue((short) 3);
            generator.writeNumberValue((byte) 4);
            generator.writeNumberValue(1.25f);
            generator.writeUuid(uuid);
            generator.writeUuid(null);
            generator.writeArrayEnd();
        }
        assertEquals("[7,2.5,3,4,1.25,\"" + uuid + "\",null]", output.toString());
    }
}
