package org.streamer;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Emits the seed, then the running result of folding the source from left to right.
 *
 * <p>Package private: an implementation detail of {@link StreamUtils}.</p>
 *
 * @param <T> the source element type
 * @param <R> the accumulated type
 */
final class ScanSpliterator<T, R> extends Spliterators.AbstractSpliterator<R> {

    private final Spliterator<T> source;
    private final BiFunction<? super R, ? super T, ? extends R> accumulator;
    private R running;
    private boolean seedEmitted;

    ScanSpliterator(Spliterator<T> source,
                    R seed,
                    BiFunction<? super R, ? super T, ? extends R> accumulator) {
        super(estimate(source), Spliterator.ORDERED);
        this.source = source;
        this.running = seed;
        this.accumulator = accumulator;
    }

    private static long estimate(Spliterator<?> source) {
        long size = source.estimateSize();
        // One more than the source: the seed is emitted before anything is consumed.
        return size == Long.MAX_VALUE ? Long.MAX_VALUE : size + 1;
    }

    @Override
    public boolean tryAdvance(Consumer<? super R> action) {
        if (!seedEmitted) {
            seedEmitted = true;
            action.accept(running);
            return true;
        }
        return source.tryAdvance(element -> {
            running = accumulator.apply(running, element);
            action.accept(running);
        });
    }
}
