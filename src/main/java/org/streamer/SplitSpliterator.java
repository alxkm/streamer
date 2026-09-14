package org.streamer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Cuts a stream into segments at the elements matching a delimiter predicate.
 *
 * <p>A segment is emitted for every delimiter, holding the elements before it, plus a final
 * segment when the stream ends with content rather than a delimiter. Delimiters are dropped.</p>
 *
 * <p>Package private: an implementation detail of {@link StreamUtils}.</p>
 *
 * @param <T> the element type
 */
final class SplitSpliterator<T> extends Spliterators.AbstractSpliterator<List<T>> {

    private final Spliterator<T> source;
    private final Predicate<? super T> delimiter;
    private List<T> current = new ArrayList<>();
    private boolean segmentReady;
    private boolean sourceExhausted;

    SplitSpliterator(Spliterator<T> source, Predicate<? super T> delimiter) {
        super(source.estimateSize(), Spliterator.ORDERED | Spliterator.NONNULL);
        this.source = source;
        this.delimiter = delimiter;
    }

    @Override
    public boolean tryAdvance(Consumer<? super List<T>> action) {
        while (!sourceExhausted) {
            boolean advanced = source.tryAdvance(this::accept);
            if (!advanced) {
                sourceExhausted = true;
                break;
            }
            if (segmentReady) {
                segmentReady = false;
                return emitCurrent(action);
            }
        }
        // Trailing content with no delimiter after it still forms a segment; a stream that ended
        // on a delimiter has already had everything emitted.
        return !current.isEmpty() && emitCurrent(action);
    }

    private void accept(T element) {
        if (delimiter.test(element)) {
            segmentReady = true;
        } else {
            current.add(element);
        }
    }

    private boolean emitCurrent(Consumer<? super List<T>> action) {
        List<T> segment = current;
        current = new ArrayList<>();
        action.accept(Collections.unmodifiableList(segment));
        return true;
    }
}
