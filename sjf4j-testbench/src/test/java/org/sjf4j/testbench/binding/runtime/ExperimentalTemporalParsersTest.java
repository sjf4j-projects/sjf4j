package org.sjf4j.testbench.binding.runtime;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Period;
import java.util.Random;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentalTemporalParsersTest {

    @Test
    void durationMatchesJdkForSupportedForms() {
        for (String text : new String[]{
                "PT20.345S", "PT15M", "PT10H", "P2D", "P2DT3H4M",
                "P2DT3H4M5.678S", "PT-6H3M", "-PT6H3M", "-PT-6H+3M",
                "PT1.S", "PT1,5S", "P-1D", "+P+1DT+2H-3M+4.5S",
                "PT+1H-2M", "-P-1DT-2H+3M-4.5S", "P1DT0S",
                "p2dt3h4m5.678s", "PT0S", "PT-0.125S"}) {
            assertSameOutcome(text, Duration::parse,
                    ExperimentalTemporalParsers::parseDuration);
        }
    }

    @Test
    void periodMatchesJdkForSupportedForms() {
        for (String text : new String[]{
                "P2Y", "P3M", "P4W", "P5D", "P1Y2M3D",
                "P1Y2M3W4D", "P-1Y2M", "-P1Y2M",
                "P+1Y-2M+3D", "p1y2m3d", "P0D",
                "P1W2D", "-P-1Y+2M-3W+4D"}) {
            assertSameOutcome(text, Period::parse,
                    ExperimentalTemporalParsers::parsePeriod);
        }
    }

    @Test
    void randomDurationRoundTrips() {
        Random random = new Random(20261031L);
        for (int i = 0; i < 20_000; i++) {
            long seconds = random.nextLong(-1_000_000_000_000L, 1_000_000_000_000L);
            Duration value = Duration.ofSeconds(seconds, random.nextInt(1_000_000_000));
            String text = value.toString();
            assertEquals(Duration.parse(text),
                    ExperimentalTemporalParsers.parseDuration(text), text);
        }
    }

    @Test
    void randomPeriodRoundTrips() {
        Random random = new Random(20261031L);
        for (int i = 0; i < 20_000; i++) {
            Period value = Period.of(random.nextInt(-100_000, 100_000),
                    random.nextInt(-100_000, 100_000),
                    random.nextInt(-100_000, 100_000));
            String text = value.toString();
            assertEquals(Period.parse(text),
                    ExperimentalTemporalParsers.parsePeriod(text), text);
        }
    }

    @Test
    void invalidAndOverflowInputsRetainJdkBehavior() {
        for (String text : new String[]{
                "", "P", "PT", "P1H", "P1D2H", "P1DT", "PT1S2M",
                "P1.5D", "PT1.5H", "PT1.1234567890S", "PT+S",
                "P999999999999999999999D", "PT9223372036854775807H"}) {
            assertSameOutcome(text, Duration::parse,
                    ExperimentalTemporalParsers::parseDuration);
        }

        for (String text : new String[]{
                "", "P", "P0", "P1.5Y", "P1Y1Y", "P1D1M",
                "P1Y1D1M", "P1Y1W1M", "P2147483648Y",
                "-P-2147483648Y", "P2147483647W"}) {
            assertSameOutcome(text, Period::parse,
                    ExperimentalTemporalParsers::parsePeriod);
        }
    }

    private static <T> void assertSameOutcome(String text,
                                               Function<String, T> jdk,
                                               Function<String, T> candidate) {
        T expected;
        try {
            expected = jdk.apply(text);
        } catch (RuntimeException expectedFailure) {
            RuntimeException actualFailure = assertThrows(expectedFailure.getClass(),
                    () -> candidate.apply(text), text);
            assertNotNull(actualFailure);
            return;
        }
        assertEquals(expected, candidate.apply(text), text);
    }
}
