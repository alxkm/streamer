package org.streamer;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Passes elements through until one matches, then stops. The matching element is emitted.
 *
 * <p>Package private: an implementation detail of {@link StreamUtils}.</p>
 *
 * @param <T> the element type
 */
final class TakeUntilSpliterator<T> extends Spliterators.AbstractSpliterator<T> {

    private final Spliterator<T> source;
    private final Predicate<? super T> stopCondition;
    private boolean done;

    TakeUntilSpliterator(Spliterator<T> source, Predicate<? super T> stopCondition) {
        super(source.estimateSize(), inheritedCharacteristics(source));
        this.source = source;
        this.stopCondition = stopCondition;
    }

    private static int inheritedCharacteristics(Spliterator<?> source) {
        // SIZED goes because the result is shorter than the source. SORTED goes because this
        // wrapper cannot answer getComparator(), and the pipeline asks whenever SORTED is set.
        return source.characteristics()
                & ~(Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.SORTED);
    }

    @Override
    public boolean tryAdvance(Consumer<? super T> action) {
        if (done) {
            return false;
        }
        return source.tryAdvance(element -> {
            action.accept(element);
            if (stopCondition.test(element)) {
                done = true;
            }
        });
    }
}
