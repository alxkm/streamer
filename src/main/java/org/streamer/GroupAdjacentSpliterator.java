package org.streamer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * Groups runs of adjacent elements that belong together.
 *
 * <p>Only the current run is buffered, so this streams inputs far larger than memory as long as no
 * single run is. A finished run is handed over by reference and replaced with a fresh list, which
 * keeps allocation proportional to the number of groups rather than to the number of elements.</p>
 *
 * <p>Package private: an implementation detail of {@link StreamUtils}.</p>
 *
 * @param <T> the element type
 */
final class GroupAdjacentSpliterator<T> extends Spliterators.AbstractSpliterator<List<T>> {

    private final Spliterator<T> source;
    private final BiPredicate<? super T, ? super T> sameGroup;
    private List<T> current = new ArrayList<>();
    private List<T> completed;
    private boolean sourceExhausted;

    GroupAdjacentSpliterator(Spliterator<T> source, BiPredicate<? super T, ? super T> sameGroup) {
        super(source.estimateSize(), Spliterator.ORDERED | Spliterator.NONNULL);
        this.source = source;
        this.sameGroup = sameGroup;
    }

    @Override
    public boolean tryAdvance(Consumer<? super List<T>> action) {
        while (!sourceExhausted) {
            boolean advanced = source.tryAdvance(this::accept);
            if (!advanced) {
                sourceExhausted = true;
                break;
            }
            if (completed != null) {
                List<T> group = completed;
                completed = null;
                action.accept(Collections.unmodifiableList(group));
                return true;
            }
        }
        if (current.isEmpty()) {
            return false;
        }
        return emitCurrent(action);
    }

    private void accept(T element) {
        if (!current.isEmpty() && !sameGroup.test(current.get(current.size() - 1), element)) {
            completed = current;
            current = new ArrayList<>();
        }
        current.add(element);
    }

    private boolean emitCurrent(Consumer<? super List<T>> action) {
        List<T> group = current;
        current = new ArrayList<>();
        action.accept(Collections.unmodifiableList(group));
        return true;
    }
}
