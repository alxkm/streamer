package org.streamer;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Compares each operator against the code someone would otherwise write by hand.
 *
 * <p>The point is not that this library is fast in the abstract. It is that reaching for it costs
 * nothing over the obvious hand written version, and that a few of the obvious versions are worse
 * than they look.</p>
 *
 * <p>Run with {@code ./gradlew jmh}.</p>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class OperatorBenchmark {

    @Param({"10000"})
    public int size;

    private List<Integer> input;
    private List<Integer> other;

    @Setup
    public void setUp() {
        input = IntStream.range(0, size).boxed().toList();
        other = IntStream.range(0, size).boxed().toList();
    }

    // -----------------------------------------------------------------
    // batch: library operator against the usual hand rolled loop
    // -----------------------------------------------------------------

    @Benchmark
    public void batch_streamer(Blackhole hole) {
        StreamUtils.batch(input.stream(), 100).forEach(hole::consume);
    }

    @Benchmark
    public void batch_handWritten(Blackhole hole) {
        Iterator<Integer> iterator = input.iterator();
        while (iterator.hasNext()) {
            List<Integer> chunk = new ArrayList<>(100);
            while (chunk.size() < 100 && iterator.hasNext()) {
                chunk.add(iterator.next());
            }
            hole.consume(chunk);
        }
    }

    // -----------------------------------------------------------------
    // Streamer against StreamUtils: the fluent wrapper must be free
    // -----------------------------------------------------------------

    @Benchmark
    public void fluent_streamer(Blackhole hole) {
        Streamer.of(input.stream()).filter(value -> value % 2 == 0).batch(100).forEach(hole::consume);
    }

    @Benchmark
    public void fluent_staticCalls(Blackhole hole) {
        StreamUtils.batch(input.stream().filter(value -> value % 2 == 0), 100).forEach(hole::consume);
    }

    // -----------------------------------------------------------------
    // windowed: against a hand written buffer, and against subList views.
    // subList does no copying at all, which is why it wins by a mile. It only works when the
    // data is already a random access List in memory.
    // -----------------------------------------------------------------

    @Benchmark
    public void windowed_streamer(Blackhole hole) {
        StreamUtils.windowed(input.stream(), 10).forEach(hole::consume);
    }

    @Benchmark
    public void windowed_handWritten(Blackhole hole) {
        // The like for like comparison: a stream source, and every window copied so that it stays
        // valid after the buffer moves on.
        java.util.ArrayDeque<Integer> buffer = new java.util.ArrayDeque<>(10);
        input.stream().forEach(value -> {
            buffer.addLast(value);
            if (buffer.size() == 10) {
                hole.consume(new ArrayList<>(buffer));
                buffer.removeFirst();
            }
        });
    }

    @Benchmark
    public void windowed_subList(Blackhole hole) {
        for (int i = 0; i + 10 <= input.size(); i++) {
            hole.consume(input.subList(i, i + 10));
        }
    }

    // -----------------------------------------------------------------
    // zip: lockstep pairing against index arithmetic over two lists
    // -----------------------------------------------------------------

    @Benchmark
    public void zip_streamer(Blackhole hole) {
        StreamUtils.zip(input.stream(), other.stream()).forEach(hole::consume);
    }

    @Benchmark
    public void zip_byIndex(Blackhole hole) {
        IntStream.range(0, Math.min(input.size(), other.size()))
                .mapToObj(i -> Pair.of(input.get(i), other.get(i)))
                .forEach(hole::consume);
    }

    // -----------------------------------------------------------------
    // concat: flat concatenation against nested Stream.concat
    // -----------------------------------------------------------------

    @Benchmark
    public void concat_streamer(Blackhole hole) {
        hole.consume(StreamUtils.concat(sources()).count());
    }

    @Benchmark
    public void concat_nested(Blackhole hole) {
        Stream<Integer> combined = Stream.empty();
        for (Stream<Integer> source : sources()) {
            combined = Stream.concat(combined, source);
        }
        hole.consume(combined.count());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Stream<Integer>[] sources() {
        // 1000 small streams, the shape that makes nested concat build a deep pipeline.
        Stream<Integer>[] streams = new Stream[1_000];
        for (int i = 0; i < streams.length; i++) {
            streams[i] = Stream.of(i, i + 1, i + 2);
        }
        return streams;
    }

    // -----------------------------------------------------------------
    // range: boxed range that stays SIZED against IntStream.boxed()
    // -----------------------------------------------------------------

    @Benchmark
    public void range_streamerParallel(Blackhole hole) {
        hole.consume(StreamUtils.range(0, size).parallel().mapToInt(Integer::intValue).sum());
    }

    @Benchmark
    public void range_intStreamBoxedParallel(Blackhole hole) {
        hole.consume(IntStream.range(0, size).boxed().parallel().mapToInt(Integer::intValue).sum());
    }
}
