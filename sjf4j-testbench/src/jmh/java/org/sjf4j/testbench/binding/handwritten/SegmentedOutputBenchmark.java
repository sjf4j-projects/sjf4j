package org.sjf4j.testbench.binding.handwritten;

import com.fasterxml.jackson.core.io.SegmentedStringWriter;
import com.fasterxml.jackson.core.util.BufferRecycler;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.binding.PooledStringWriter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Large char-output comparison. Jackson's original unrestricted recycler is
 * included separately from an equivalent <=256K retained-char-array policy.
 *
 * The experimental sink stays entirely in testbench: production is unchanged.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(1)
@State(Scope.Thread)
public class SegmentedOutputBenchmark {
    private static final int CHUNK = 256;
    private static final int MAX_RETAINED = 256 * 1024;

    @Param({"262145", "307200", "1048576"})
    public int payloadLength;
    @Param({"ASCII", "UNICODE"})
    public String alphabet;
    @Param({"131072"})
    public int segmentSize;

    private String data;
    private BufferRecycler unlimitedRecycler;
    private BufferRecycler cappedRecycler;

    private static final class CappedBufferRecycler extends BufferRecycler {
        @Override
        public void releaseCharBuffer(int kind, char[] array) {
            if (array.length <= MAX_RETAINED) super.releaseCharBuffer(kind, array);
        }
    }

    @Setup(Level.Trial)
    public void prepare() throws IOException {
        String pattern = "ASCII".equals(alphabet) ? "hello-1234" : "中文测试例";
        data = pattern.repeat((payloadLength + pattern.length() - 1) / pattern.length())
                .substring(0, payloadLength);

        unlimitedRecycler = new BufferRecycler();
        cappedRecycler = new CappedBufferRecycler();
        validateJackson(unlimitedRecycler);
        validateJackson(cappedRecycler);
        validateSegmented(SegmentedOutputWriter.Merge.BUILDER, data);
        validateSegmented(SegmentedOutputWriter.Merge.ARRAY, data);
        // Check exact cap and crossover, char[] write, single-character write,
        // and the final incomplete segment independently of JMH scores.
        for (int size : new int[]{1, 1024, 262144, 262145, 262146, 393217}) {
            String sample = ("世界abc").repeat(size / 5 + 1).substring(0, size);
            validateSegmented(SegmentedOutputWriter.Merge.BUILDER, sample);
            validateSegmented(SegmentedOutputWriter.Merge.ARRAY, sample);
        }
    }

    private void validateJackson(BufferRecycler recycler) throws IOException {
        SegmentedStringWriter writer = new SegmentedStringWriter(recycler);
        writer.write(data);
        if (!data.equals(writer.getAndClear()))
            throw new IllegalStateException("Jackson result differs");
    }

    private void validateSegmented(SegmentedOutputWriter.Merge merge, String expected) {
        SegmentedOutputWriter whole = new SegmentedOutputWriter(segmentSize, merge);
        try {
            whole.write(expected);
            if (whole.size() != expected.length() ||
                    !expected.equals(whole.getAndRecycle()))
                throw new IllegalStateException("Invalid segmented bulk result: " + merge);
        } finally {
            whole.recycle();
        }
        SegmentedOutputWriter chunks = new SegmentedOutputWriter(segmentSize, merge);
        try {
            for (int i = 0; i < expected.length(); i += CHUNK)
                chunks.write(expected, i, Math.min(CHUNK, expected.length() - i));
            if (!expected.equals(chunks.getAndRecycle()))
                throw new IllegalStateException("Invalid segmented chunk result: " + merge);
        } finally {
            chunks.recycle();
        }
        SegmentedOutputWriter array = new SegmentedOutputWriter(segmentSize, merge);
        try {
            char[] chars = expected.toCharArray();
            if (chars.length != 0) {
                array.write(chars, 0, chars.length - 1);
                array.write(chars[chars.length - 1]);
            }
            if (!expected.equals(array.getAndRecycle()))
                throw new IllegalStateException("Invalid segmented char[] result: " + merge);
        } finally {
            array.recycle();
        }
    }

    private String segmented(SegmentedOutputWriter.Merge mode, boolean chunks) {
        SegmentedOutputWriter out = new SegmentedOutputWriter(segmentSize, mode);
        try {
            if (chunks) {
                for (int i = 0; i < data.length(); i += CHUNK)
                    out.write(data, i, Math.min(CHUNK, data.length() - i));
            } else out.write(data);
            return out.getAndRecycle();
        } finally { out.recycle(); }
    }

    private String jackson(BufferRecycler recycler, boolean chunks) throws IOException {
        SegmentedStringWriter out = new SegmentedStringWriter(recycler);
        if (chunks) {
            for (int i = 0; i < data.length(); i += CHUNK)
                out.write(data, i, Math.min(CHUNK, data.length() - i));
        } else out.write(data);
        return out.getAndClear();
    }

    @Benchmark
    public String currentPooled() {
        PooledStringWriter out = new PooledStringWriter();
        try {
            out.write(data);
            return out.getAndRecycle();
        } finally { out.recycle(); }
    }

    @Benchmark
    public String chunksCurrentPooled() {
        PooledStringWriter out = new PooledStringWriter();
        try {
            for (int i = 0; i < data.length(); i += CHUNK)
                out.write(data, i, Math.min(CHUNK, data.length() - i));
            return out.getAndRecycle();
        } finally { out.recycle(); }
    }

    @Benchmark
    public String segmentedBuilder() {
        return segmented(SegmentedOutputWriter.Merge.BUILDER, false);
    }

    @Benchmark
    public String chunksSegmentedBuilder() {
        return segmented(SegmentedOutputWriter.Merge.BUILDER, true);
    }

    @Benchmark
    public String segmentedArray() {
        return segmented(SegmentedOutputWriter.Merge.ARRAY, false);
    }

    @Benchmark
    public String chunksSegmentedArray() {
        return segmented(SegmentedOutputWriter.Merge.ARRAY, true);
    }

    @Benchmark
    public String jacksonUnlimited() throws IOException {
        return jackson(unlimitedRecycler, false);
    }

    @Benchmark
    public String chunksJacksonUnlimited() throws IOException {
        return jackson(unlimitedRecycler, true);
    }

    @Benchmark
    public String jacksonCapped() throws IOException {
        return jackson(cappedRecycler, false);
    }

    @Benchmark
    public String chunksJacksonCapped() throws IOException {
        return jackson(cappedRecycler, true);
    }

    @Benchmark
    public String directStringBuilder() {
        StringBuilder out = new StringBuilder(data.length());
        out.append(data);
        return out.toString();
    }

    @Benchmark
    public String chunksDirectStringBuilder() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < data.length(); i += CHUNK)
            out.append(data, i, Math.min(data.length(), i + CHUNK));
        return out.toString();
    }
}
