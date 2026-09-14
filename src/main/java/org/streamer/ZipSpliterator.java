package org.streamer;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Walks two spliterators in lockstep and combines the elements pairwise.
 *
 * <p>Iteration stops as soon as either side runs out, so the result is as long as the shorter
 * input. The spliterator is not splittable: pairing element {@code i} of the left input with
 * element {@code i} of the right input is inherently sequential unless both sides are
 * {@code SUBSIZED}, which is rarely the case.</p>
 *
 * <p>Package private: an implementation detail of {@link StreamUtils}.</p>
 *
 * @param <A> the element type of the left input
 * @param <B> the element type of the right input
 * @param <R> the combined result type
 */
final class ZipSpliterator<A, B, R> extends Spliterators.AbstractSpliterator<R> {

    private final Spliterator<A> left;
    private final Spliterator<B> right;
    private final BiFunction<? super A, ? super B, ? extends R> combiner;
    private final Holder<A> leftValue = new Holder<>();
    private final Holder<B> rightValue = new Holder<>();

    /**
     * Creates a zipping spliterator.
     *
     * @param left     the left input
     * @param right    the right input
     * @param combiner the function applied to each pair of elements
     */
    public ZipSpliterator(Spliterator<A> left,
                          Spliterator<B> right,
                          BiFunction<? super A, ? super B, ? extends R> combiner) {
        super(Math.min(left.estimateSize(), right.estimateSize()), Spliterator.ORDERED);
        this.left = left;
        this.right = right;
        this.combiner = combiner;
    }

    @Override
    public boolean tryAdvance(Consumer<? super R> action) {
        if (!left.tryAdvance(leftValue)) {
            return false;
        }
        if (!right.tryAdvance(rightValue)) {
            return false;
        }
        action.accept(combiner.apply(leftValue.value, rightValue.value));
        return true;
    }

    /** Single slot sink used to pull one element out of a spliterator. */
    private static final class Holder<T> implements Consumer<T> {
        private T value;

        @Override
        public void accept(T t) {
            this.value = t;
        }
    }
}
