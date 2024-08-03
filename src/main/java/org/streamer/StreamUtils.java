package org.streamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class StreamUtils {
    private static final Logger logger = LogManager.getLogger(StreamUtils.class);

    private StreamUtils() {}

    /**
     * Returns a collector that accumulates elements into a list while ensuring uniqueness.
     * @param <T> the type of the input elements
     * @return a collector that accumulates elements into a list while ensuring uniqueness
     */
    public static <T> Collector<T, Set<T>, List<T>> unique() {
        return Collector.of(HashSet::new,
                            Set::add,
                            StreamUtils::addToSet,
                            ArrayList::new,
                            Collector.Characteristics.CONCURRENT,
                            Collector.Characteristics.UNORDERED);
    }

    /**
     * Merges two sets by adding all elements from the second set to the first set.
     * @param <T> the type of elements in the sets
     * @param first the first set
     * @param second the second set to be merged into the first set
     * @return the merged set containing elements from both input sets
     */
    private static <T> Set<T> addToSet(Set<T> first, Set<T> second) {
        first.addAll(second);
        return first;
    }

    /**
     * Converts an Iterator into a Stream.
     * @param <T> the type of elements in the Iterator and Stream
     * @param iterator the Iterator to be converted
     * @return a Stream containing elements from the Iterator
     */
    public static <T> Stream<T> asStream(Iterator<T> iterator) {
        Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.IMMUTABLE);
        return StreamSupport.stream(spliterator, false);
    }

    /**
     * Converts an Iterable into a Stream.
     * @param <T> the type of elements in the Iterable and Stream
     * @param iterable the Iterable to be converted
     * @return a Stream containing elements from the Iterable
     */
    public static <T> Stream<T> asStream(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Converts an array to a collection of the specified type.
     * @param <T> the type of elements in the array and collection
     * @param collectionType the class representing the desired collection type
     * @param array the array to be converted to a collection
     * @return a collection containing elements from the array
     * @throws IllegalArgumentException if the collection creation fails
     */
    public static <T> Collection<T> arrayToCollection(final Class<? extends Collection> collectionType, final T[] array) {
        try {
            final Collection<T> collection = collectionType.getDeclaredConstructor().newInstance();
            collection.addAll(Arrays.asList(array));
            return collection;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            logger.error("Error during collections creation", e);
            throw new IllegalArgumentException("Failed to create collection of specified type");
        }
    }

    /**
     * Filters elements of a stream by a specific type and casts them to that type.
     *
     * @param stream the original stream
     * @param clazz the class to filter by
     * @param <T> the type of the elements to filter
     * @return a stream containing only elements of the specified type
     */
    public static <T> Stream<T> filterByType(Stream<?> stream, Class<T> clazz) {
        return stream.filter(clazz::isInstance).map(clazz::cast);
    }

    /**
     * Collects elements of a stream into a list.
     *
     * @param stream the original stream
     * @param <T> the type of the elements
     * @return a list containing all elements of the stream
     */
    public static <T> List<T> toList(Stream<T> stream) {
        return stream.collect(Collectors.toList());
    }

    /**
     * Collects elements of a stream into a set.
     *
     * @param stream the original stream
     * @param <T> the type of the elements
     * @return a set containing all elements of the stream
     */
    public static <T> Set<T> toSet(Stream<T> stream) {
        return stream.collect(Collectors.toSet());
    }

    /**
     * Collects elements of a stream into a map.
     *
     * @param stream the original stream
     * @param keyMapper a function to generate keys
     * @param valueMapper a function to generate values
     * @param <T> the type of the elements
     * @param <K> the type of the keys
     * @param <U> the type of the values
     * @return a map containing keys and values generated from the elements of the stream
     */
    public static <T, K, U> Map<K, U> toMap(Stream<T> stream, Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends U> valueMapper) {
        return stream.collect(Collectors.toMap(keyMapper, valueMapper));
    }

    /**
     * Groups elements of a stream by a classifier function.
     *
     * @param stream the original stream
     * @param classifier a function to classify elements
     * @param <T> the type of the elements
     * @param <K> the type of the classifier
     * @return a map where the keys are the classifier values and the values are lists of elements
     */
    public static <T, K> Map<K, List<T>> groupBy(Stream<T> stream, Function<? super T, ? extends K> classifier) {
        return stream.collect(Collectors.groupingBy(classifier));
    }

    /**
     * Partitions elements of a stream by a predicate.
     *
     * @param stream the original stream
     * @param predicate a predicate to partition elements
     * @param <T> the type of the elements
     * @return a map with two entries: true and false. Each entry contains a list of elements that match (or do not match) the predicate.
     */
    public static <T> Map<Boolean, List<T>> partitionBy(Stream<T> stream, Predicate<? super T> predicate) {
        return stream.collect(Collectors.partitioningBy(predicate));
    }

    /**
     * Returns a stream containing a single element if the element is non-null, otherwise returns an empty stream.
     *
     * @param element the element
     * @param <T> the type of the element
     * @return a stream containing the element, or an empty stream if the element is null
     */
    public static <T> Stream<T> streamOfNullable(T element) {
        return element == null ? Stream.empty() : Stream.of(element);
    }

    /**
     * Concatenates multiple streams into one stream.
     *
     * @param streams the streams to concatenate
     * @param <T> the type of the elements
     * @return a single stream containing all elements of the input streams
     */
    @SafeVarargs
    public static <T> Stream<T> concatStreams(Stream<T>... streams) {
        return Arrays.stream(streams).flatMap(Function.identity());
    }

    /**
     * Zips two streams into a single stream of pairs.
     *
     * @param a the first stream
     * @param b the second stream
     * @param <A> the type of elements in the first stream
     * @param <B> the type of elements in the second stream
     * @return a stream of pairs where each pair contains one element from each of the input streams
     */
    public static <A, B> Stream<Pair<A, B>> zip(Stream<A> a, Stream<B> b) {
        Iterator<A> iteratorA = a.iterator();
        Iterator<B> iteratorB = b.iterator();
        Iterable<Pair<A, B>> iterable = () -> new Iterator<>() {
            public boolean hasNext() {
                return iteratorA.hasNext() && iteratorB.hasNext();
            }

            public Pair<A, B> next() {
                return new Pair<>(iteratorA.next(), iteratorB.next());
            }
        };
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Performs an action on each element of a stream and returns the stream.
     *
     * @param stream the original stream
     * @param action the action to perform
     * @param <T> the type of the elements
     * @return the same stream with the action applied to each element
     */
    public static <T> Stream<T> peekAndReturn(Stream<T> stream, Consumer<? super T> action) {
        return stream.peek(action);
    }

    /**
     * Finds the first element of a stream, if present.
     *
     * @param stream the original stream
     * @param <T> the type of the elements
     * @return an optional containing the first element, or an empty optional if the stream is empty
     */
    public static <T> Optional<T> findFirst(Stream<T> stream) {
        return stream.findFirst();
    }

    /**
     * Batches elements of a stream into lists of a given size.
     *
     * @param stream the original stream
     * @param batchSize the size of the batches
     * @param <T> the type of the elements
     * @return a stream of lists, each containing up to batchSize elements
     */
    public static <T> Stream<List<T>> batchProcess(Stream<T> stream, int batchSize) {
        Iterator<T> iterator = stream.iterator();
        Iterable<List<T>> iterable = () -> new Iterator<>() {
            public boolean hasNext() {
                return iterator.hasNext();
            }

            public List<T> next() {
                List<T> batch = new ArrayList<>(batchSize);
                for (int i = 0; i < batchSize && iterator.hasNext(); i++) {
                    batch.add(iterator.next());
                }
                return batch;
            }
        };
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Filters elements of a stream in parallel based on a predicate.
     *
     * @param stream the original stream
     * @param predicate the predicate to filter elements
     * @param <T> the type of the elements
     * @return a parallel stream containing only elements that match the predicate
     */
    public static <T> Stream<T> parallelFilter(Stream<T> stream, Predicate<? super T> predicate) {
        return stream.parallel().filter(predicate);
    }

    /**
     * Maps elements of a stream in parallel using a mapper function.
     *
     * @param stream the original stream
     * @param mapper the mapper function to apply to elements
     * @param <T> the type of the elements
     * @param <R> the type of the resulting elements
     * @return a parallel stream containing the mapped elements
     */
    public static <T, R> Stream<R> parallelMap(Stream<T> stream, Function<? super T, ? extends R> mapper) {
        return stream.parallel().map(mapper);
    }

    /**
     * Creates a predicate that maintains state to allow only distinct elements based on a key extractor function.
     *
     * @param keyExtractor the function to extract keys
     * @param <T> the type of the elements
     * @return a predicate that allows only distinct elements based on the extracted keys
     */
    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    /**
     * Creates a stream from an iterator.
     *
     * @param iterator the iterator
     * @param <T> the type of the elements
     * @return a stream containing all elements of the iterator
     */
    public static <T> Stream<T> streamify(Iterator<T> iterator) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
    }

    /**
     * Creates a stream from an iterable.
     *
     * @param iterable the iterable
     * @param <T> the type of the elements
     * @return a stream containing all elements of the iterable
     */
    public static <T> Stream<T> streamify(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Flattens a stream of collections to a stream of pairs.
     *
     * @param stream the original stream
     * @param mapper the function to generate a stream from each element
     * @param <T> the type of the elements in the original stream
     * @param <U> the type of the elements in the generated streams
     * @return a stream of pairs where each pair contains an element from the original stream and an element from the generated stream
     */
    public static <T, U> Stream<Pair<T, U>> flatMapToPair(Stream<T> stream, Function<? super T, Stream<U>> mapper) {
        return stream.flatMap(t -> mapper.apply(t).map(u -> new Pair<>(t, u)));
    }

    /**
     * Filters elements that do not match the given predicate.
     *
     * @param stream the original stream
     * @param predicate the predicate to filter elements
     * @param <T> the type of the elements
     * @return a stream containing only elements that do not match the predicate
     */
    public static <T> Stream<T> filterNot(Stream<T> stream, Predicate<? super T> predicate) {
        return stream.filter(predicate.negate());
    }

    /**
     * Takes elements while the predicate is true.
     *
     * @param stream the original stream
     * @param predicate the predicate to test elements
     * @param <T> the type of the elements
     * @return a stream containing only elements that match the predicate until it returns false
     */
    public static <T> Stream<T> takeWhile(Stream<T> stream, Predicate<? super T> predicate) {
        Spliterator<T> spliterator = stream.spliterator();
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<T>(spliterator.estimateSize(), 0) {
            boolean stillGoing = true;

            public boolean tryAdvance(Consumer<? super T> action) {
                if (stillGoing) {
                    boolean hadNext = spliterator.tryAdvance(elem -> {
                        if (predicate.test(elem)) {
                            action.accept(elem);
                        } else {
                            stillGoing = false;
                        }
                    });
                    return hadNext && stillGoing;
                }
                return false;
            }
        }, false);
    }

    /**
     * Maps elements to their index positions.
     *
     * @param stream the original stream
     * @param <T> the type of the elements
     * @return a stream of pairs where each pair contains the index and the corresponding element
     */
    public static <T> Stream<Pair<Integer, T>> mapToIndex(Stream<T> stream) {
        AtomicInteger index = new AtomicInteger(0);
        return stream.map(elem -> new Pair<>(index.getAndIncrement(), elem));
    }

    static class RangeSpliterator implements Spliterator<Integer> {
        private final int start;
        private final int end;
        private int current;

        public RangeSpliterator(int start, int end) {
            this.start = start;
            this.end = end;
            this.current = start;
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
        public Spliterator<Integer> trySplit() {
            int mid = (current + end) >>> 1;
            if (mid <= current) {
                return null;
            }
            int oldCurrent = current;
            current = mid;
            return new RangeSpliterator(oldCurrent, mid);
        }

        @Override
        public long estimateSize() {
            return end - current;
        }

        @Override
        public int characteristics() {
            return ORDERED | SIZED | SUBSIZED | IMMUTABLE;
        }
    }

    public static Stream<Integer> customRangeAsStream(int start, int end) {
        Spliterator<Integer> spliterator = new RangeSpliterator(start, end);
        return StreamSupport.stream(spliterator, false);
    }
}

// Helper Pair class
class Pair<A, B> {
    public final A first;
    public final B second;

    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public String toString() {
        return "Pair{" +
                "first=" + first +
                ", second=" + second +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return Objects.equals(first, pair.first) &&
                Objects.equals(second, pair.second);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }
}