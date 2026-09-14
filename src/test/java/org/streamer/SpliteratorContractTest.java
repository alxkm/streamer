package org.streamer;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the parts of the {@link Spliterator} contract that the stream pipeline relies on but
 * that a plain "collect and compare" test would not exercise.
 */
class SpliteratorContractTest {

    @Test
    void rangeReportsItsSizeExactly() {
        RangeSpliterator spliterator = new RangeSpliterator(10, 25);

        assertThat(spliterator.estimateSize()).isEqualTo(15);
        assertThat(spliterator.getExactSizeIfKnown()).isEqualTo(15);
        assertThat(spliterator.hasCharacteristics(Spliterator.SIZED | Spliterator.SUBSIZED)).isTrue();
    }

    @Test
    void rangeSplitsIntoTwoDisjointHalves() {
        RangeSpliterator source = new RangeSpliterator(0, 100);

        Spliterator<Integer> prefix = source.trySplit();

        assertThat(prefix).isNotNull();
        assertThat(prefix.estimateSize() + source.estimateSize()).isEqualTo(100);
        assertThat(drain(prefix)).containsExactlyElementsOf(range(0, 50));
        assertThat(drain(source)).containsExactlyElementsOf(range(50, 100));
    }

    @Test
    void rangeSplitsANegativeRangeCorrectly() {
        // Regression: (current + end) >>> 1 turned a negative midpoint into a huge positive one,
        // so the prefix claimed billions of elements outside the range.
        RangeSpliterator source = new RangeSpliterator(-1_000, -500);

        Spliterator<Integer> prefix = source.trySplit();

        assertThat(prefix).isNotNull();
        assertThat(prefix.estimateSize() + source.estimateSize()).isEqualTo(500);
        assertThat(drain(prefix)).containsExactlyElementsOf(range(-1_000, -750));
        assertThat(drain(source)).containsExactlyElementsOf(range(-750, -500));
    }

    @Test
    void rangeSplitsTheWidestPossibleRange() {
        RangeSpliterator source = new RangeSpliterator(Integer.MIN_VALUE, Integer.MAX_VALUE);

        Spliterator<Integer> prefix = source.trySplit();

        assertThat(prefix).isNotNull();
        assertThat(prefix.estimateSize() + source.estimateSize())
                .isEqualTo((long) Integer.MAX_VALUE - Integer.MIN_VALUE);
    }

    @Test
    void rangeRefusesToSplitASingleElement() {
        assertThat(new RangeSpliterator(7, 8).trySplit()).isNull();
        assertThat(new RangeSpliterator(7, 7).trySplit()).isNull();
    }

    @Test
    void rangeSurvivesRepeatedSplitting() {
        List<Integer> collected = new ArrayList<>();
        List<Spliterator<Integer>> pending = new ArrayList<>();
        pending.add(new RangeSpliterator(0, 64));

        while (!pending.isEmpty()) {
            Spliterator<Integer> current = pending.remove(pending.size() - 1);
            Spliterator<Integer> half = current.trySplit();
            if (half == null) {
                current.forEachRemaining(collected::add);
            } else {
                pending.add(current);
                pending.add(half);
            }
        }

        assertThat(collected).hasSize(64).containsExactlyInAnyOrderElementsOf(range(0, 64));
    }

    @Test
    void rangeForEachRemainingDrainsExactlyOnce() {
        RangeSpliterator spliterator = new RangeSpliterator(0, 3);
        List<Integer> collected = new ArrayList<>();

        spliterator.forEachRemaining(collected::add);
        spliterator.forEachRemaining(collected::add);

        assertThat(collected).containsExactly(0, 1, 2);
        assertThat(spliterator.tryAdvance(collected::add)).isFalse();
    }

    @Test
    void batchDerivesItsSizeFromTheSource() {
        Spliterator<Integer> source = IntStream.range(0, 10).boxed().spliterator();

        BatchSpliterator<Integer> batched = new BatchSpliterator<>(source, 3);

        assertThat(batched.estimateSize()).isEqualTo(4);
        assertThat(batched.hasCharacteristics(Spliterator.ORDERED)).isTrue();
    }

    @Test
    void batchFallsBackToAnUnknownSizeForAnUnsizedSource() {
        Spliterator<Integer> unsized = java.util.Spliterators.spliteratorUnknownSize(
                List.of(1, 2, 3).iterator(), Spliterator.ORDERED);

        assertThat(new BatchSpliterator<>(unsized, 2).estimateSize()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void windowEstimatesTheNumberOfCompleteWindows() {
        Spliterator<Integer> source = IntStream.range(0, 10).boxed().spliterator();

        assertThat(new WindowSpliterator<>(source, 3, 2).estimateSize()).isEqualTo(4);
    }

    @Test
    void windowEstimatesZeroWhenTheSourceIsTooShort() {
        Spliterator<Integer> source = IntStream.range(0, 2).boxed().spliterator();

        assertThat(new WindowSpliterator<>(source, 5, 1).estimateSize()).isZero();
    }

    @Test
    void zipEstimatesTheShorterInput() {
        Spliterator<Integer> left = IntStream.range(0, 10).boxed().spliterator();
        Spliterator<Integer> right = IntStream.range(0, 4).boxed().spliterator();

        assertThat(new ZipSpliterator<>(left, right, (a, b) -> a + b).estimateSize()).isEqualTo(4);
    }

    @Test
    void theSpliteratorsAreNotPartOfThePublicApi() {
        // Package private, so no caller outside org.streamer can name them, on the class path or
        // the module path. module-info exports org.streamer and nothing else.
        List<Class<?>> spliterators = List.of(RangeSpliterator.class, BatchSpliterator.class,
                WindowSpliterator.class, ZipSpliterator.class);

        assertThat(spliterators)
                .allSatisfy(type -> assertThat(Modifier.isPublic(type.getModifiers()))
                        .as("%s must stay package private", type.getSimpleName())
                        .isFalse());
    }

    private static List<Integer> drain(Spliterator<Integer> spliterator) {
        List<Integer> collected = new ArrayList<>();
        spliterator.forEachRemaining(collected::add);
        return collected;
    }

    private static List<Integer> range(int start, int end) {
        return IntStream.range(start, end).boxed().toList();
    }
}
