package org.streamer;

import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * A splittable spliterator over a half open range of boxed integers.
 *
 * <p>This is the reference implementation behind {@link StreamUtils#range(int, int)}.
 * It reports {@code SIZED | SUBSIZED}, so the stream pipeline knows the exact element count up
 * front and can split the range evenly across the fork/join pool instead of guessing.</p>
 *
 * <p>Package private: an implementation detail of {@link StreamUtils}.</p>
 */
final class RangeSpliterator implements Spliterator<Integer> {

    private final int end;
    private int current;

    /**
     * Creates a spliterator over {@code [startInclusive, endExclusive)}.
     *
     * @param startInclusive the first value of the range
     * @param endExclusive   the upper bound, exclusive
     */
    public RangeSpliterator(int startInclusive, int endExclusive) {
        this.current = startInclusive;
        this.end = Math.max(startInclusive, endExclusive);
    }

    @Override
    public boolean tryAdvance(Consumer<? super Integer> action) {
        if (current < end) {
            action.accept(current++);
            return true;
        }
        return false;
    }

    @Override
    public void forEachRemaining(Consumer<? super Integer> action) {
        for (int i = current; i < end; i++) {
            action.accept(i);
        }
        current = end;
    }

    @Override
    public Spliterator<Integer> trySplit() {
        // The sum is computed as a long and shifted arithmetically. The usual (a + b) >>> 1 trick
        // only holds for non-negative bounds: on a negative range it turns the midpoint into a
        // huge positive number and the split covers billions of elements that are not in the range.
        int mid = (int) (((long) current + (long) end) >> 1);
        if (mid <= current) {
            return null;
        }
        int prefixStart = current;
        current = mid;
        return new RangeSpliterator(prefixStart, mid);
    }

    @Override
    public long estimateSize() {
        return (long) end - current;
    }

    @Override
    public long getExactSizeIfKnown() {
        return estimateSize();
    }

    @Override
    public int characteristics() {
        return ORDERED | SIZED | SUBSIZED | IMMUTABLE | NONNULL | DISTINCT | SORTED;
    }

    @Override
    public java.util.Comparator<? super Integer> getComparator() {
        // SORTED is reported with natural ordering.
        return null;
    }
}
