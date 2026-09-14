package org.streamer;

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * An immutable pair of two values.
 *
 * <p>The JDK has no general purpose tuple, so operations such as
 * {@link StreamUtils#zip(java.util.stream.Stream, java.util.stream.Stream)} need one to
 * express their result. {@code Pair} fills that gap: it is a plain carrier with value
 * semantics, and it accepts {@code null} in either position.</p>
 *
 * <pre>{@code
 * Pair<String, Integer> p = Pair.of("a", 1);
 * p.first();            // "a"
 * p.swap();             // Pair["a" -> 1] becomes Pair[1 -> "a"]
 * p.mapSecond(i -> i * 2);
 * }</pre>
 *
 * @param <A>    the type of the first value
 * @param <B>    the type of the second value
 * @param first  the first value, may be {@code null}
 * @param second the second value, may be {@code null}
 */
public record Pair<A, B>(A first, B second) {

    /**
     * Creates a pair of the two given values.
     *
     * @param first  the first value, may be {@code null}
     * @param second the second value, may be {@code null}
     * @param <A>    the type of the first value
     * @param <B>    the type of the second value
     * @return a new pair
     */
    public static <A, B> Pair<A, B> of(A first, B second) {
        return new Pair<>(first, second);
    }

    /**
     * Creates a pair from a map entry.
     *
     * @param entry the entry to convert
     * @param <K>   the key type
     * @param <V>   the value type
     * @return a pair holding the entry key and value
     */
    public static <K, V> Pair<K, V> fromEntry(Map.Entry<K, V> entry) {
        return new Pair<>(entry.getKey(), entry.getValue());
    }

    /**
     * Returns a comparator that orders pairs by their first value, then by their second.
     *
     * @param firstComparator  the comparator for the first value
     * @param secondComparator the comparator for the second value
     * @param <A>              the type of the first value
     * @param <B>              the type of the second value
     * @return a comparator over pairs
     */
    public static <A, B> Comparator<Pair<A, B>> comparing(Comparator<? super A> firstComparator,
                                                          Comparator<? super B> secondComparator) {
        return Comparator.<Pair<A, B>, A>comparing(Pair::first, firstComparator)
                .thenComparing(Pair::second, secondComparator);
    }

    /**
     * Returns a pair with the two values exchanged.
     *
     * @return a new pair holding {@code (second, first)}
     */
    public Pair<B, A> swap() {
        return new Pair<>(second, first);
    }

    /**
     * Applies a function to the first value and keeps the second one unchanged.
     *
     * @param mapper the function to apply
     * @param <R>    the type of the new first value
     * @return a new pair
     */
    public <R> Pair<R, B> mapFirst(Function<? super A, ? extends R> mapper) {
        return new Pair<>(mapper.apply(first), second);
    }

    /**
     * Applies a function to the second value and keeps the first one unchanged.
     *
     * @param mapper the function to apply
     * @param <R>    the type of the new second value
     * @return a new pair
     */
    public <R> Pair<A, R> mapSecond(Function<? super B, ? extends R> mapper) {
        return new Pair<>(first, mapper.apply(second));
    }

    /**
     * Collapses the pair into a single value.
     *
     * @param combiner the function applied to both values
     * @param <R>      the result type
     * @return the combined value
     */
    public <R> R fold(BiFunction<? super A, ? super B, ? extends R> combiner) {
        return combiner.apply(first, second);
    }

    /**
     * Converts the pair into an immutable map entry.
     *
     * <p>Unlike {@link Map#entry(Object, Object)} the result tolerates {@code null} values.</p>
     *
     * @return an immutable entry holding the same values
     */
    public Map.Entry<A, B> toEntry() {
        return new AbstractMap.SimpleImmutableEntry<>(first, second);
    }
}
