package org.sjf4j.testbench.binding.runtime;

import org.openjdk.jmh.annotations.*;

import java.time.Duration;
import java.time.Period;
import java.util.concurrent.TimeUnit;

/**
 * Isolates JDK regex-based ISO parsing from an experimental hand-written
 * scanner. The scanner remains testbench-only until the results justify
 * production code and direct backend integration.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(2)
public class DurationPeriodParseBenchmark {

    @Param({"simple", "fraction", "composite", "signed"})
    public String shape;

    private String durationText;
    private String periodText;

    @Setup(Level.Trial)
    public void setup() {
        switch (shape) {
            case "simple":
                durationText = "PT15M";
                periodText = "P5D";
                break;
            case "fraction":
                durationText = "PT20.345S";
                periodText = "P3M";
                break;
            case "composite":
                durationText = "P2DT3H4M5.678S";
                periodText = "P1Y2M3W4D";
                break;
            case "signed":
                durationText = "-PT-6H+3M-4.5S";
                periodText = "-P-1Y+2M-3W+4D";
                break;
            default:
                throw new IllegalArgumentException(shape);
        }
    }

    @Benchmark
    public Duration durationJdk() {
        return Duration.parse(durationText);
    }

    @Benchmark
    public Duration durationExperimental() {
        return ExperimentalTemporalParsers.parseDuration(durationText);
    }

    @Benchmark
    public Period periodJdk() {
        return Period.parse(periodText);
    }

    @Benchmark
    public Period periodExperimental() {
        return ExperimentalTemporalParsers.parsePeriod(periodText);
    }
}
