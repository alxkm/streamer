package org.streamer;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Stream operators that the JDK does not ship.
 *
 * <p>Every method here exists because writing it by hand at the call site is either verbose or
 * easy to get wrong. Operators the JDK already covers in one line are deliberately absent: use
 * {@code stream.toList()}, {@code Collectors.groupingBy} and friends directly.</p>
 *
 * <h2>Groups</h2>
 * <ul>
 *   <li><b>Creation</b> - {@link #asStream(Iterator)}, {@link #asStream(Iterable)},
 *       {@link #asStream(Enumeration)}, {@link #ofNullable(Object)}, {@link #range(int, int)},
 *       {@link #concat(Stream[])}</li>
 *   <li><b>Filtering</b> - {@link #filterByType(Stream, Class)},
 *       {@link #filterNot(Stream, Predicate)}, {@link #distinctBy(Stream, Function)},
 *       {@link #distinctByKey(Function)}, {@link #takeUntil(Stream, Predicate)}</li>
 *   <li><b>Reshaping</b> - {@link #zip(Stream, Stream)}, {@link #zip(Stream, Stream, BiFunction)},
 *       {@link #zipWithIndex(Stream)}, {@link #batch(Stream, int)}, {@link #windowed(Stream, int)},
 *       {@link #windowed(Stream, int, int)}, {@link #groupAdjacent(Stream, BiPredicate)},
 *       {@link #groupAdjacentBy(Stream, Function)}, {@link #splitBy(Stream, Predicate)},
 *       {@link #scan(Stream, Object, BiFunction)}, {@link #flatMapToPair(Stream, Function)}</li>
 *   <li><b>Merging</b> - {@link #interleave(Stream, Stream)},
 *       {@link #mergeSorted(Stream, Stream, Comparator)}</li>
 *   <li><b>Conversion</b> - {@link #arrayToCollection(Supplier, Object[])},
 *       {@link #arrayToCollection(Class, Object[])}</li>
 * </ul>
 *
 * <h2>Laziness and parallelism</h2>
 * <p>Intermediate operators return lazy streams: nothing is consumed until a terminal operation
 * runs. Operators built on a custom spliterator ({@code zip}, {@code batch}, {@code windowed},
 * {@code scan}, {@code interleave}, {@code mergeSorted}, {@code groupAdjacent},
 * {@code groupAdjacentBy}, {@code splitBy}, {@code zipWithIndex}) are order dependent and do not
 * split, so calling {@code parallel()} on
 * their result parallelises only the stages after them. Each returned stream inherits
 * {@code close} from its source, so a stream over a closeable resource still needs
 * try-with-resources.</p>
 */
public final class StreamUtils {

    private StreamUtils() {
        throw new AssertionError("No instances");
    }

    // ---------------------------------------------------------------------
    // Creation
    // ---------------------------------------------------------------------

    /**
     * Wraps an {@link Iterator} in a sequential stream.
     *
     * <p>The iterator is consumed lazily. Its size is unknown, so the pipeline cannot size its
     * buffers up front.</p>
     *
     * <pre>{@code
     * Iterator<String> it = List.of("a", "b", "c").iterator();
     * List<String> upper = StreamUtils.asStream(it).map(String::toUpperCase).toList();
     * }</pre>
     *
     * @param iterator the iterator to wrap
     * @param <T>      the element type
     * @return a sequential stream over the remaining elements
     * @throws NullPointerException if {@code iterator} is null
     */
    public static <T> Stream<T> asStream(Iterator<T> iterator) {
        Objects.requireNonNull(iterator, "iterator");
        Spliterator<T> spliterator =
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED);
        return StreamSupport.stream(spliterator, false);
    }

    /**
     * Wraps an {@link Iterable} in a sequential stream.
     *
     * @param iterable the iterable to wrap
     * @param <T>      the element type
     * @return a sequential stream over the elements
     * @throws NullPointerException if {@code iterable} is null
     */
    public static <T> Stream<T> asStream(Iterable<T> iterable) {
        Objects.requireNonNull(iterable, "iterable");
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Wraps a legacy {@link Enumeration} in a sequential stream.
     *
     * <p>Useful for pre-collections APIs such as {@code ServletRequest.getHeaderNames()} or
     * {@code ZipFile.entries()}.</p>
     *
     * @param enumeration the enumeration to wrap
     * @param <T>         the element type
     * @return a sequential stream over the remaining elements
     * @throws NullPointerException if {@code enumeration} is null
     */
    public static <T> Stream<T> asStream(Enumeration<T> enumeration) {
        Objects.requireNonNull(enumeration, "enumeration");
        return asStream(new Iterator<T>() {
            @Override
            public boolean hasNext() {
                return enumeration.hasMoreElements();
            }

            @Override
            public T next() {
                return enumeration.nextElement();
            }
        });
    }

    /**
     * Returns a single element stream, or an empty stream when the element is null.
     *
     * @param element the element, may be null
     * @param <T>     the element type
     * @return a stream of zero or one element
     */
    public static <T> Stream<T> ofNullable(T element) {
        return element == null ? Stream.empty() : Stream.of(element);
    }

    /**
     * Returns a stream of boxed integers over {@code [startInclusive, endExclusive)}.
     *
     * <p>Unlike {@code IntStream.range(..).boxed()} this keeps the {@code SIZED} and
     * {@code SUBSIZED} characteristics of a range all the way through, so the values split evenly
     * when the stream is made parallel. An empty stream is returned when the range is empty or
     * inverted.</p>
     *
     * @param startInclusive the first value
     * @param endExclusive   the upper bound, exclusive
     * @return a stream over the range
     */
    public static Stream<Integer> range(int startInclusive, int endExclusive) {
        return StreamSupport.stream(new RangeSpliterator(startInclusive, endExclusive), false);
    }

    /**
     * Concatenates several streams into one.
     *
     * <p>Prefer this over nesting {@link Stream#concat(Stream, Stream)}, which builds a left
     * leaning tree and degrades for long chains.</p>
     *
     * @param streams the streams to concatenate
     * @param <T>     the element type
     * @return a stream over all elements, in argument order
     * @throws NullPointerException if the array or any stream in it is null
     */
    @SafeVarargs
    @SuppressWarnings("varargs") // The array is only read, never stored or published.
    public static <T> Stream<T> concat(Stream<? extends T>... streams) {
        Objects.requireNonNull(streams, "streams");
        for (Stream<? extends T> stream : streams) {
            Objects.requireNonNull(stream, "streams contains a null element");
        }
        return Arrays.stream(streams).flatMap(Function.identity());
    }

    // ---------------------------------------------------------------------
    // Filtering
    // ---------------------------------------------------------------------

    /**
     * Keeps only the elements that are instances of the given type and casts them.
     *
     * <pre>{@code
     * Stream<Object> mixed = Stream.of(1, "two", 3, "four");
     * List<String> strings = StreamUtils.filterByType(mixed, String.class).toList(); // [two, four]
     * }</pre>
     *
     * @param stream the source stream
     * @param type   the type to keep
     * @param <T>    the resulting element type
     * @return a stream of the matching elements
     * @throws NullPointerException if any argument is null
     */
    public static <T> Stream<T> filterByType(Stream<?> stream, Class<T> type) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(type, "type");
        return stream.filter(type::isInstance).map(type::cast);
    }

    /**
     * Keeps the elements that do <em>not</em> match the predicate.
     *
     * <p>Reads better than {@code filter(p.negate())}, especially when the predicate is a method
     * reference.</p>
     *
     * @param stream    the source stream
     * @param predicate the predicate to negate
     * @param <T>       the element type
     * @return a stream of the non matching elements
     * @throws NullPointerException if any argument is null
     */
    public static <T> Stream<T> filterNot(Stream<T> stream, Predicate<? super T> predicate) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(predicate, "predicate");
        return stream.filter(predicate.negate());
    }

    /**
     * Keeps the first element for each distinct key, discarding later duplicates.
     *
     * <pre>{@code
     * List<Person> onePerCity = StreamUtils.distinctBy(people.stream(), Person::city).toList();
     * }</pre>
     *
     * @param stream       the source stream
     * @param keyExtractor the function producing the key to compare on
     * @param <T>          the element type
     * @return a stream without key duplicates
     * @throws NullPointerException if any argument is null
     */
    public static <T> Stream<T> distinctBy(Stream<T> stream, Function<? super T, ?> keyExtractor) {
        Objects.requireNonNull(stream, "stream");
        return stream.filter(distinctByKey(keyExtractor));
    }

    /**
     * Returns a stateful predicate that admits an element only the first time its key is seen.
     *
     * <p>Backed by a concurrent set, so the predicate is safe on parallel streams. It is single
     * use: each call creates a fresh predicate with its own state, and reusing one across two
     * pipelines leaks the keys of the first into the second.</p>
     *
     * <pre>{@code
     * stream.filter(StreamUtils.distinctByKey(Person::email))
     * }</pre>
     *
     * @param keyExtractor the function producing the key to compare on
     * @param <T>          the element type
     * @return a stateful predicate for use with {@link Stream#filter(Predicate)}
     * @throws NullPointerException if {@code keyExtractor} is null
     */
    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Objects.requireNonNull(keyExtractor, "keyExtractor");
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return element -> seen.add(keyExtractor.apply(element));
    }

    /**
     * Takes elements up to and including the first one matching the predicate.
     *
     * <p>The complement of {@link Stream#takeWhile(Predicate)}, which stops <em>before</em> the
     * matching element. Handy for consuming a paged feed until the terminator page arrives.</p>
     *
     * <pre>{@code
     * StreamUtils.takeUntil(Stream.of(1, 2, 3, 4), x -> x == 3).toList(); // [1, 2, 3]
     * }</pre>
     *
     * @param stream    the source stream
     * @param predicate the stop condition
     * @param <T>       the element type
     * @return a stream ending at the first matching element
     * @throws NullPointerException if any argument is null
     */
    public static <T> Stream<T> takeUntil(Stream<T> stream, Predicate<? super T> predicate) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(predicate, "predicate");
        return derive(stream, new TakeUntilSpliterator<>(stream.spliterator(), predicate));
    }

    // ---------------------------------------------------------------------
    // Reshaping
    // ---------------------------------------------------------------------

    /**
     * Pairs up two streams element by element.
     *
     * <p>The result is as long as the shorter input; surplus elements of the longer one are never
     * pulled.</p>
     *
     * <pre>{@code
     * StreamUtils.zip(Stream.of("a", "b"), Stream.of(1, 2, 3)).toList();
     * // [Pair[first=a, second=1], Pair[first=b, second=2]]
     * }</pre>
     *
     * @param left  the first stream
     * @param right the second stream
     * @param <A>   the element type of the first stream
     * @param <B>   the element type of the second stream
     * @return a stream of pairs
     * @throws NullPointerException if any argument is null
     */
    public static <A, B> Stream<Pair<A, B>> zip(Stream<A> left, Stream<B> right) {
        return zip(left, right, Pair::of);
    }

    /**
     * Pairs up two streams element by element and combines each pair.
     *
     * <pre>{@code
     * StreamUtils.zip(names, scores, (name, score) -> name + ":" + score).toList();
     * }</pre>
     *
     * @param left     the first stream
     * @param right    the second stream
     * @param combiner the function applied to each pair of elements
     * @param <A>      the element type of the first stream
     * @param <B>      the element type of the second stream
     * @param <R>      the combined type
     * @return a stream of combined values, as long as the shorter input
     * @throws NullPointerException if any argument is null
     */
    public static <A, B, R> Stream<R> zip(Stream<A> left,
                                          Stream<B> right,
                                          BiFunction<? super A, ? super B, ? extends R> combiner) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(combiner, "combiner");
        Spliterator<R> zipped =
                new ZipSpliterator<>(left.spliterator(), right.spliterator(), combiner);
        return StreamSupport.stream(zipped, false)
                .onClose(left::close)
                .onClose(right::close);
    }

    /**
     * Pairs each element with its zero based position.
     *
     * <p>The stream is forced sequential: positions are only meaningful in encounter order.</p>
     *
     * <pre>{@code
     * StreamUtils.zipWithIndex(Stream.of("a", "b")).toList();
     * // [Pair[first=0, second=a], Pair[first=1, second=b]]
     * }</pre>
     *
     * @param stream the source stream
     * @param <T>    the element type
     * @return a stream of index and element pairs
     * @throws NullPointerException if {@code stream} is null
     */
    public static <T> Stream<Pair<Integer, T>> zipWithIndex(Stream<T> stream) {
        Objects.requireNonNull(stream, "stream");
        // A long counter narrowed on every step, so element 2^31 throws instead of wrapping to a
        // negative index that would silently corrupt whatever consumes it.
        long[] next = {0};
        return stream.sequential().map(element -> Pair.of(Math.toIntExact(next[0]++), element));
    }

    /**
     * Groups consecutive elements into immutable lists of the given size.
     *
     * <p>The final batch is shorter when the element count is not a multiple of
     * {@code batchSize}. Nothing is buffered beyond a single batch, so this works on streams too
     * large to hold in memory.</p>
     *
     * <pre>{@code
     * StreamUtils.batch(rows, 500).forEach(repository::saveAll);
     * }</pre>
     *
     * @param stream    the source stream
     * @param batchSize the number of elements per batch
     * @param <T>       the element type
     * @return a stream of batches
     * @throws NullPointerException     if {@code stream} is null
     * @throws IllegalArgumentException if {@code batchSize} is less than one
     */
    public static <T> Stream<List<T>> batch(Stream<T> stream, int batchSize) {
        Objects.requireNonNull(stream, "stream");
        requirePositive(batchSize, "batchSize");
        return derive(stream, new BatchSpliterator<>(stream.spliterator(), batchSize));
    }

    /**
     * Emits every sliding window of the given size, advancing one element at a time.
     *
     * <p>Equivalent to {@code windowed(stream, size, 1)}. A stream shorter than the window
     * produces no output.</p>
     *
     * <pre>{@code
     * // three day moving average
     * StreamUtils.windowed(prices, 3)
     *            .mapToDouble(w -> w.stream().mapToDouble(Double::doubleValue).average().orElse(0))
     *            .toArray();
     * }</pre>
     *
     * @param stream the source stream
     * @param size   the window size
     * @param <T>    the element type
     * @return a stream of overlapping windows
     * @throws NullPointerException     if {@code stream} is null
     * @throws IllegalArgumentException if {@code size} is less than one
     */
    public static <T> Stream<List<T>> windowed(Stream<T> stream, int size) {
        return windowed(stream, size, 1);
    }

    /**
     * Emits sliding windows of the given size, advancing by {@code step} elements.
     *
     * <p>Only complete windows are emitted: a trailing partial window is dropped. Use
     * {@link #batch(Stream, int)} when the tail matters.</p>
     *
     * @param stream the source stream
     * @param size   the window size
     * @param step   how far the window advances between emissions
     * @param <T>    the element type
     * @return a stream of windows
     * @throws NullPointerException     if {@code stream} is null
     * @throws IllegalArgumentException if {@code size} or {@code step} is less than one
     */
    public static <T> Stream<List<T>> windowed(Stream<T> stream, int size, int step) {
        Objects.requireNonNull(stream, "stream");
        requirePositive(size, "size");
        requirePositive(step, "step");
        return derive(stream, new WindowSpliterator<>(stream.spliterator(), size, step));
    }

    /**
     * Groups runs of adjacent elements that belong together.
     *
     * <p>A new group starts whenever {@code sameGroup} returns false for the previous element and
     * the current one. Unlike {@code Collectors.groupingBy} this keeps encounter order, streams
     * lazily and only buffers the current run.</p>
     *
     * <pre>{@code
     * // split a log into runs of the same level
     * StreamUtils.groupAdjacent(events, (a, b) -> a.level() == b.level())
     * }</pre>
     *
     * @param stream    the source stream
     * @param sameGroup tests whether two adjacent elements belong to the same group
     * @param <T>       the element type
     * @return a stream of immutable groups, each with at least one element
     * @throws NullPointerException if any argument is null
     */
    public static <T> Stream<List<T>> groupAdjacent(Stream<T> stream,
                                                    BiPredicate<? super T, ? super T> sameGroup) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(sameGroup, "sameGroup");
        return derive(stream, new GroupAdjacentSpliterator<>(stream.spliterator(), sameGroup));
    }

    /**
     * Groups runs of adjacent elements that produce the same key.
     *
     * <p>The readable form of {@link #groupAdjacent(Stream, BiPredicate)} when the grouping is
     * decided by a property rather than by a relation. Keys are compared with
     * {@link Objects#equals(Object, Object)} and each element's key is computed once.</p>
     *
     * <pre>{@code
     * // one group per run of log lines at the same level
     * StreamUtils.groupAdjacentBy(events, Event::level)
     * }</pre>
     *
     * @param stream       the source stream
     * @param keyExtractor the function producing the grouping key
     * @param <T>          the element type
     * @param <K>          the key type
     * @return a stream of immutable groups, each with at least one element
     * @throws NullPointerException if any argument is null
     */
    public static <T, K> Stream<List<T>> groupAdjacentBy(
            Stream<T> stream, Function<? super T, ? extends K> keyExtractor) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(keyExtractor, "keyExtractor");
        return groupAdjacent(stream, new BiPredicate<T, T>() {
            private T lastRight;
            private K lastRightKey;

            @Override
            public boolean test(T left, T right) {
                // Elements arrive as overlapping pairs: (a,b) then (b,c) then (c,d). Remembering
                // the key of the previous right operand keeps this at one call per element.
                K leftKey = left == lastRight ? lastRightKey : keyExtractor.apply(left);
                K rightKey = keyExtractor.apply(right);
                lastRight = right;
                lastRightKey = rightKey;
                return Objects.equals(leftKey, rightKey);
            }
        });
    }

    /**
     * Cuts the stream into segments at the elements matching the delimiter, dropping the
     * delimiters themselves.
     *
     * <p>One segment is emitted per delimiter, holding whatever came before it, plus a final
     * segment when the stream ends with content rather than a delimiter. Segments can therefore be
     * empty, for two delimiters in a row; chain {@code .filter(not(List::isEmpty))} when that is
     * not wanted. Only the current segment is buffered.</p>
     *
     * <pre>{@code
     * // records separated by blank lines
     * StreamUtils.splitBy(Files.lines(path), String::isBlank)
     * }</pre>
     *
     * @param stream    the source stream
     * @param delimiter identifies the elements that separate segments
     * @param <T>       the element type
     * @return a stream of immutable segments
     * @throws NullPointerException if any argument is null
     */
    public static <T> Stream<List<T>> splitBy(Stream<T> stream, Predicate<? super T> delimiter) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(delimiter, "delimiter");
        return derive(stream, new SplitSpliterator<>(stream.spliterator(), delimiter));
    }

    /**
     * Emits the running result of folding the stream from left to right.
     *
     * <p>Where {@code reduce} returns only the final value, {@code scan} returns every
     * intermediate one, starting with the seed. The output therefore has one more element than
     * the input.</p>
     *
     * <pre>{@code
     * StreamUtils.scan(Stream.of(1, 2, 3), 0, Integer::sum).toList(); // [0, 1, 3, 6]
     * }</pre>
     *
     * @param stream      the source stream
     * @param seed        the initial value, emitted first
     * @param accumulator combines the running value with the next element
     * @param <T>         the element type
     * @param <R>         the accumulated type
     * @return a stream of running values
     * @throws NullPointerException if {@code stream} or {@code accumulator} is null
     */
    public static <T, R> Stream<R> scan(Stream<T> stream,
                                        R seed,
                                        BiFunction<? super R, ? super T, ? extends R> accumulator) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(accumulator, "accumulator");
        return derive(stream, new ScanSpliterator<>(stream.spliterator(), seed, accumulator));
    }

    /**
     * Expands each element into a stream and pairs every produced value with its source element.
     *
     * <pre>{@code
     * StreamUtils.flatMapToPair(orders.stream(), order -> order.items().stream());
     * // one pair per item, each carrying its order
     * }</pre>
     *
     * @param stream the source stream
     * @param mapper produces the values to pair with each element
     * @param <T>    the source element type
     * @param <U>    the produced value type
     * @return a stream of source and value pairs
     * @throws NullPointerException if any argument is null
     */
    public static <T, U> Stream<Pair<T, U>> flatMapToPair(
            Stream<T> stream, Function<? super T, ? extends Stream<U>> mapper) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(mapper, "mapper");
        return stream.flatMap(element -> mapper.apply(element).map(value -> Pair.of(element, value)));
    }

    // ---------------------------------------------------------------------
    // Merging
    // ---------------------------------------------------------------------

    /**
     * Alternates between two streams, taking one element from each in turn.
     *
     * <p>Once one side is exhausted the remainder of the other is appended.</p>
     *
     * <pre>{@code
     * StreamUtils.interleave(Stream.of(1, 3, 5), Stream.of(2, 4)).toList(); // [1, 2, 3, 4, 5]
     * }</pre>
     *
     * @param left  the stream to draw from first
     * @param right the stream to draw from second
     * @param <T>   the element type
     * @return the interleaved stream
     * @throws NullPointerException if any argument is null
     */
    public static <T> Stream<T> interleave(Stream<? extends T> left, Stream<? extends T> right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Iterator<? extends T> leftIterator = left.iterator();
        Iterator<? extends T> rightIterator = right.iterator();
        Iterator<T> merged = new Iterator<>() {
            private boolean preferLeft = true;

            @Override
            public boolean hasNext() {
                return leftIterator.hasNext() || rightIterator.hasNext();
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                boolean fromLeft = preferLeft ? leftIterator.hasNext() : !rightIterator.hasNext();
                preferLeft = !preferLeft;
                return fromLeft ? leftIterator.next() : rightIterator.next();
            }
        };
        return asStream(merged).onClose(left::close).onClose(right::close);
    }

    /**
     * Merges two streams that are already sorted, preserving the ordering.
     *
     * <p>Ties are resolved in favour of the left stream, which makes the merge stable. Only one
     * element from each side is held at a time, so this merges inputs far larger than memory, for
     * example two sorted files. Null elements are allowed as long as the comparator accepts them,
     * for example {@code Comparator.nullsFirst(naturalOrder())}.</p>
     *
     * @param left       the first sorted stream
     * @param right      the second sorted stream
     * @param comparator the ordering both inputs follow
     * @param <T>        the element type
     * @return a sorted stream over all elements of both inputs
     * @throws NullPointerException if any argument is null
     */
    public static <T> Stream<T> mergeSorted(Stream<? extends T> left,
                                            Stream<? extends T> right,
                                            Comparator<? super T> comparator) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(comparator, "comparator");
        Iterator<? extends T> leftIterator = left.iterator();
        Iterator<? extends T> rightIterator = right.iterator();
        Iterator<T> merged = new Iterator<>() {
            private T leftHead;
            private T rightHead;
            // Explicit flags rather than a null head, so null elements stay legal as long as the
            // comparator accepts them.
            private boolean hasLeft = pullLeft();
            private boolean hasRight = pullRight();

            @Override
            public boolean hasNext() {
                return hasLeft || hasRight;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                boolean fromLeft =
                        !hasRight || (hasLeft && comparator.compare(leftHead, rightHead) <= 0);
                T element;
                if (fromLeft) {
                    element = leftHead;
                    hasLeft = pullLeft();
                } else {
                    element = rightHead;
                    hasRight = pullRight();
                }
                return element;
            }

            private boolean pullLeft() {
                boolean available = leftIterator.hasNext();
                leftHead = available ? leftIterator.next() : null;
                return available;
            }

            private boolean pullRight() {
                boolean available = rightIterator.hasNext();
                rightHead = available ? rightIterator.next() : null;
                return available;
            }
        };
        return asStream(merged).onClose(left::close).onClose(right::close);
    }

    // ---------------------------------------------------------------------
    // Conversion
    // ---------------------------------------------------------------------

    /**
     * Copies an array into a new collection produced by the given factory.
     *
     * <p>Type safe and reflection free; prefer this over
     * {@link #arrayToCollection(Class, Object[])}.</p>
     *
     * <pre>{@code
     * TreeSet<String> sorted = StreamUtils.arrayToCollection(TreeSet::new, new String[] {"b", "a"});
     * }</pre>
     *
     * @param factory supplies the empty collection to fill
     * @param array   the array to copy
     * @param <T>     the element type
     * @param <C>     the collection type
     * @return the collection returned by the factory, filled with the array elements
     * @throws NullPointerException if any argument is null, or the factory returns null
     */
    public static <T, C extends Collection<T>> C arrayToCollection(Supplier<C> factory, T[] array) {
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(array, "array");
        C collection = Objects.requireNonNull(factory.get(), "factory returned null");
        Collections.addAll(collection, array);
        return collection;
    }

    /**
     * Copies an array into a new collection of the given type, instantiated reflectively.
     *
     * <p>Kept for callers that only know the collection type at runtime. When the type is known at
     * compile time use {@link #arrayToCollection(Supplier, Object[])} instead: it is checked by
     * the compiler and does not need an accessible no-argument constructor.</p>
     *
     * @param collectionType the collection class to instantiate
     * @param array          the array to copy
     * @param <T>            the element type
     * @return a collection of the requested type holding the array elements
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if the type has no accessible no-argument constructor, or
     *                                  the constructor throws
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> Collection<T> arrayToCollection(Class<? extends Collection> collectionType,
                                                      T[] array) {
        Objects.requireNonNull(collectionType, "collectionType");
        Objects.requireNonNull(array, "array");
        try {
            Collection<T> collection =
                    (Collection<T>) collectionType.getDeclaredConstructor().newInstance();
            Collections.addAll(collection, array);
            return collection;
        } catch (InstantiationException | IllegalAccessException
                 | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    "Cannot instantiate " + collectionType.getName()
                            + ": a public no-argument constructor is required", e);
        }
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    /**
     * Builds a stream over {@code spliterator} that keeps the close handler of {@code source}, so
     * resources behind the original stream are still released.
     */
    private static <T, R> Stream<R> derive(Stream<T> source, Spliterator<R> spliterator) {
        return StreamSupport.stream(spliterator, false).onClose(source::close);
    }

    private static void requirePositive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be at least 1, but was " + value);
        }
    }
}
