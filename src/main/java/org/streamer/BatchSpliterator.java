package org.streamer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

/**
 * Groups the elements of a source spliterator into consecutive fixed size lists.
 *
 * <p>The last batch is shorter when the source size is not a multiple of the batch size.
 * The size estimate is derived from the source so that {@code SIZED} pipelines keep a
 * useful estimate instead of falling back to {@link Long#MAX_VALUE}.</p>
 *
 * <p>Package private: an implementation detail of {@link StreamUtils}.</p>
 *
 * @param <T> the element type of the source
 */
final class BatchSpliterator<T> extends Spliterators.AbstractSpliterator<List<T>> {

    private final Spliterator<T> source;
    private final int batchSize;

    /**
     * Creates a batching spliterator.
     *
     * @param source    the spliterator to consume
     * @param batchSize the number of elements per batch, at least one
     */
    public BatchSpliterator(Spliterator<T> source, int batchSize) {
        super(estimate(source, batchSize), inheritedCharacteristics(source));
        this.source = source;
        this.batchSize = batchSize;
    }

    private static long estimate(Spliterator<?> source, int batchSize) {
        long size = source.estimateSize();
        if (size == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (size + batchSize - 1) / batchSize;
    }

    private static int inheritedCharacteristics(Spliterator<?> source) {
        int characteristics = Spliterator.NONNULL;
        if (source.hasCharacteristics(Spliterator.ORDERED)) {
            characteristics |= Spliterator.ORDERED;
        }
        if (source.hasCharacteristics(Spliterator.SIZED)) {
            characteristics |= Spliterator.SIZED;
        }
        return characteristics;
    }

    @Override
    public boolean tryAdvance(Consumer<? super List<T>> action) {
        // batch::add looks like it allocates a capture per element, and hoisting it into a field
        // looks like the fix. Measured, that change made this benchmark 1.75x slower: the fresh
        // capture is monomorphic and scalar replaced, while a field held Consumer is neither.
        // Leave it alone. See src/jmh and the numbers in README.md.
        List<T> batch = new ArrayList<>(batchSize);
        while (batch.size() < batchSize && source.tryAdvance(batch::add)) {
            // Filling the batch happens in the loop condition.
        }
        if (batch.isEmpty()) {
            return false;
        }
        action.accept(Collections.unmodifiableList(batch));
        return true;
    }
}
