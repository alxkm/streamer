package org.streamer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

/**
 * Emits sliding windows of a fixed size over a source spliterator.
 *
 * <p>Windows advance by {@code step} elements. With {@code step == 1} every window overlaps its
 * predecessor by {@code size - 1} elements; with {@code step == size} the behaviour matches
 * {@link BatchSpliterator} except that a trailing partial window is dropped.</p>
 *
 * <p>Package private: an implementation detail of {@link StreamUtils}.</p>
 *
 * @param <T> the element type
 */
final class WindowSpliterator<T> extends Spliterators.AbstractSpliterator<List<T>> {

    private final Spliterator<T> source;
    private final int size;
    private final int step;
    private final Deque<T> buffer;
    private boolean sourceExhausted;

    /**
     * Creates a sliding window spliterator.
     *
     * @param source the spliterator to consume
     * @param size   the window size, at least one
     * @param step   how far the window advances between emissions, at least one
     */
    public WindowSpliterator(Spliterator<T> source, int size, int step) {
        super(estimate(source, size, step), Spliterator.ORDERED | Spliterator.NONNULL);
        this.source = source;
        this.size = size;
        this.step = step;
        this.buffer = new ArrayDeque<>(size);
    }

    /** Non capturing, so the JVM hands out the same instance every time. */
    private static final Consumer<Object> DISCARD = ignored -> { };

    private static long estimate(Spliterator<?> source, int size, int step) {
        long sourceSize = source.estimateSize();
        if (sourceSize == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        if (sourceSize < size) {
            return 0;
        }
        return (sourceSize - size) / step + 1;
    }

    @Override
    public boolean tryAdvance(Consumer<? super List<T>> action) {
        if (sourceExhausted) {
            return false;
        }
        while (buffer.size() < size) {
            if (!source.tryAdvance(buffer::addLast)) {
                sourceExhausted = true;
                return false;
            }
        }
        action.accept(Collections.unmodifiableList(new ArrayList<>(buffer)));
        // Drop the elements the next window slides past. When step exceeds the window size the
        // buffer empties and the surplus is skipped directly from the source.
        for (int i = 0; i < step; i++) {
            if (!buffer.isEmpty()) {
                buffer.removeFirst();
            } else if (!source.tryAdvance(DISCARD)) {
                sourceExhausted = true;
                return true;
            }
        }
        return true;
    }
}
