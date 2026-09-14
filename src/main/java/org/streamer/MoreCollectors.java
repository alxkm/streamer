package org.streamer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collector;

/**
 * Collectors that complement {@link java.util.stream.Collectors}.
 *
 * <p>All collectors here are sequential-safe and parallel-safe: none of them declares
 * {@link Collector.Characteristics#CONCURRENT}, so the stream pipeline merges per-thread
 * containers instead of sharing one across threads.</p>
 */
public final class MoreCollectors {

    private MoreCollectors() {
        throw new AssertionError("No instances");
    }

    /**
     * Collects distinct elements into a list, keeping the order in which they first appeared.
     *
     * <p>{@code stream.distinct().toList()} does the same thing when the pipeline is sequential.
     * This collector is the version to reach for when the deduplication has to happen at the
     * collection step, for example downstream of {@code Collectors.groupingBy}:</p>
     *
     * <pre>{@code
     * Map<String, List<String>> tagsPerAuthor =
     *     posts.stream().collect(groupingBy(Post::author,
     *                            mapping(Post::tag, MoreCollectors.unique())));
     * }</pre>
     *
     * @param <T> the element type
     * @return a collector producing a mutable list of distinct elements
     */
    public static <T> Collector<T, ?, List<T>> unique() {
        return Collector.<T, Set<T>, List<T>>of(
                LinkedHashSet::new,
                Set::add,
                MoreCollectors::mergeSets,
                ArrayList::new);
    }

    /**
     * Counts how many times each element occurs.
     *
     * <pre>{@code
     * Map<String, Long> wordCounts = words.stream().collect(MoreCollectors.toFrequencyMap());
     * }</pre>
     *
     * @param <T> the element type
     * @return a collector producing a map from element to occurrence count
     */
    public static <T> Collector<T, ?, Map<T, Long>> toFrequencyMap() {
        return Collector.<T, Map<T, Long>>of(
                HashMap::new,
                (map, element) -> map.merge(element, 1L, Long::sum),
                (left, right) -> {
                    right.forEach((key, count) -> left.merge(key, count, Long::sum));
                    return left;
                },
                Collector.Characteristics.IDENTITY_FINISH,
                Collector.Characteristics.UNORDERED);
    }

    /**
     * Finds the smallest and the largest element in a single pass.
     *
     * <p>{@code min()} followed by {@code max()} would need two passes, which a stream does not
     * allow.</p>
     *
     * <pre>{@code
     * Optional<Pair<Integer, Integer>> bounds =
     *     Stream.of(3, 1, 4).collect(MoreCollectors.minMax(Comparator.naturalOrder()));
     * // Optional[Pair[first=1, second=4]]
     * }</pre>
     *
     * @param comparator the ordering to use
     * @param <T>        the element type
     * @return a collector producing the minimum and maximum, empty for an empty stream
     * @throws NullPointerException if {@code comparator} is null
     */
    public static <T> Collector<T, ?, Optional<Pair<T, T>>> minMax(Comparator<? super T> comparator) {
        Objects.requireNonNull(comparator, "comparator");
        return Collector.of(
                () -> new MinMax<T>(comparator),
                MinMax::accept,
                MinMax::combine,
                MinMax::result,
                Collector.Characteristics.UNORDERED);
    }

    /**
     * Collects into a {@link LinkedHashMap}, preserving encounter order and rejecting duplicates.
     *
     * <p>{@code Collectors.toMap} gives no ordering guarantee and reports duplicate keys with a
     * message that does not name the key. This one keeps insertion order and says which key
     * collided.</p>
     *
     * @param keyMapper   produces the map key
     * @param valueMapper produces the map value
     * @param <T>         the element type
     * @param <K>         the key type
     * @param <V>         the value type
     * @return a collector producing an ordered map
     * @throws NullPointerException if any argument is null
     */
    public static <T, K, V> Collector<T, ?, Map<K, V>> toLinkedMap(
            Function<? super T, ? extends K> keyMapper,
            Function<? super T, ? extends V> valueMapper) {
        Objects.requireNonNull(keyMapper, "keyMapper");
        Objects.requireNonNull(valueMapper, "valueMapper");
        return Collector.of(
                LinkedHashMap::new,
                (map, element) -> put(map, keyMapper.apply(element), valueMapper.apply(element)),
                (left, right) -> {
                    right.forEach((key, value) -> put(left, key, value));
                    return left;
                },
                Collector.Characteristics.IDENTITY_FINISH);
    }

    private static <K, V> void put(Map<K, V> map, K key, V value) {
        if (map.containsKey(key)) {
            throw new IllegalStateException(
                    "Duplicate key " + key + " (values " + map.get(key) + " and " + value + ")");
        }
        map.put(key, value);
    }

    private static <T> Set<T> mergeSets(Set<T> left, Set<T> right) {
        left.addAll(right);
        return left;
    }

    /** Mutable accumulator tracking the smallest and largest element seen so far. */
    private static final class MinMax<T> {
        private final Comparator<? super T> comparator;
        private T min;
        private T max;
        private boolean empty = true;

        private MinMax(Comparator<? super T> comparator) {
            this.comparator = comparator;
        }

        private void accept(T element) {
            if (empty) {
                min = element;
                max = element;
                empty = false;
                return;
            }
            if (comparator.compare(element, min) < 0) {
                min = element;
            }
            if (comparator.compare(element, max) > 0) {
                max = element;
            }
        }

        private MinMax<T> combine(MinMax<T> other) {
            if (!other.empty) {
                accept(other.min);
                accept(other.max);
            }
            return this;
        }

        private Optional<Pair<T, T>> result() {
            return empty ? Optional.empty() : Optional.of(Pair.of(min, max));
        }
    }
}
