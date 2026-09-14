package org.streamer;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collector;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * A {@link Stream} that also carries the operators of {@link StreamUtils}.
 *
 * <p>The static form reads inside out once two operators are combined:</p>
 *
 * <pre>{@code
 * StreamUtils.batch(StreamUtils.windowed(source, 3), 2)   // applied right to left
 * }</pre>
 *
 * <p>The fluent form reads in the order the data flows, and mixes freely with the JDK operators
 * because {@code Streamer} <em>is</em> a {@code Stream}:</p>
 *
 * <pre>{@code
 * Streamer.of(source)
 *         .filter(Row::isValid)
 *         .windowed(3)
 *         .batch(2)
 *         .toList();
 * }</pre>
 *
 * <p>Every JDK intermediate operation is overridden to return a {@code Streamer} again, so the
 * chain never has to be re-wrapped. Terminal operations, primitive streams and the operators that
 * do not apply behave exactly as they do on a plain stream. The class adds no state of its own: it
 * delegates to the wrapped stream, which keeps laziness, ordering, parallelism and {@code close}
 * semantics identical to calling {@link StreamUtils} directly.</p>
 *
 * @param <T> the element type
 */
public final class Streamer<T> implements Stream<T> {

    private final Stream<T> delegate;

    private Streamer(Stream<T> delegate) {
        this.delegate = delegate;
    }

    // ---------------------------------------------------------------------
    // Entry points
    // ---------------------------------------------------------------------

    /**
     * Wraps an existing stream.
     *
     * <p>Returns the argument unchanged when it is already a {@code Streamer}.</p>
     *
     * @param stream the stream to wrap
     * @param <T>    the element type
     * @return a fluent view of the stream
     * @throws NullPointerException if {@code stream} is null
     */
    public static <T> Streamer<T> of(Stream<T> stream) {
        Objects.requireNonNull(stream, "stream");
        return stream instanceof Streamer<T> streamer ? streamer : new Streamer<>(stream);
    }

    /**
     * Starts a stream over the given elements.
     *
     * @param elements the elements
     * @param <T>      the element type
     * @return a fluent stream over the elements
     * @throws NullPointerException if {@code elements} is null
     */
    @SafeVarargs
    @SuppressWarnings("varargs") // The array is only read, never stored or published.
    public static <T> Streamer<T> of(T... elements) {
        Objects.requireNonNull(elements, "elements");
        return new Streamer<>(Stream.of(elements));
    }

    /**
     * Starts a stream over an iterable.
     *
     * @param iterable the iterable to read
     * @param <T>      the element type
     * @return a fluent stream over the elements
     * @throws NullPointerException if {@code iterable} is null
     */
    public static <T> Streamer<T> from(Iterable<T> iterable) {
        return new Streamer<>(StreamUtils.asStream(iterable));
    }

    /**
     * Starts a stream over an iterator.
     *
     * @param iterator the iterator to read
     * @param <T>      the element type
     * @return a fluent stream over the remaining elements
     * @throws NullPointerException if {@code iterator} is null
     */
    public static <T> Streamer<T> from(Iterator<T> iterator) {
        return new Streamer<>(StreamUtils.asStream(iterator));
    }

    /**
     * Starts an empty stream.
     *
     * @param <T> the element type
     * @return an empty fluent stream
     */
    public static <T> Streamer<T> empty() {
        return new Streamer<>(Stream.empty());
    }

    /**
     * Starts a stream over {@code [startInclusive, endExclusive)}.
     *
     * @param startInclusive the first value
     * @param endExclusive   the upper bound, exclusive
     * @return a fluent stream over the range
     * @see StreamUtils#range(int, int)
     */
    public static Streamer<Integer> range(int startInclusive, int endExclusive) {
        return new Streamer<>(StreamUtils.range(startInclusive, endExclusive));
    }

    // ---------------------------------------------------------------------
    // Streamer operators
    // ---------------------------------------------------------------------

    /**
     * Keeps the elements that do not match the predicate.
     *
     * @param predicate the predicate to negate
     * @return the remaining elements
     * @see StreamUtils#filterNot(Stream, Predicate)
     */
    public Streamer<T> filterNot(Predicate<? super T> predicate) {
        return new Streamer<>(StreamUtils.filterNot(delegate, predicate));
    }

    /**
     * Keeps only the elements of the given type and casts them.
     *
     * @param type the type to keep
     * @param <R>  the resulting element type
     * @return the matching elements
     * @see StreamUtils#filterByType(Stream, Class)
     */
    public <R> Streamer<R> filterByType(Class<R> type) {
        return new Streamer<>(StreamUtils.filterByType(delegate, type));
    }

    /**
     * Keeps the first element for each distinct key.
     *
     * @param keyExtractor the function producing the key to compare on
     * @return the elements without key duplicates
     * @see StreamUtils#distinctBy(Stream, Function)
     */
    public Streamer<T> distinctBy(Function<? super T, ?> keyExtractor) {
        return new Streamer<>(StreamUtils.distinctBy(delegate, keyExtractor));
    }

    /**
     * Takes elements up to and including the first one matching the predicate.
     *
     * @param predicate the stop condition
     * @return the prefix ending at the first match
     * @see StreamUtils#takeUntil(Stream, Predicate)
     */
    public Streamer<T> takeUntil(Predicate<? super T> predicate) {
        return new Streamer<>(StreamUtils.takeUntil(delegate, predicate));
    }

    /**
     * Pairs each element with the element at the same position of the other stream.
     *
     * @param other the stream to pair with
     * @param <U>   the element type of the other stream
     * @return pairs, as many as the shorter input has elements
     * @see StreamUtils#zip(Stream, Stream)
     */
    public <U> Streamer<Pair<T, U>> zip(Stream<U> other) {
        return new Streamer<>(StreamUtils.zip(delegate, other));
    }

    /**
     * Pairs each element with the element at the same position of the other stream and combines
     * the two.
     *
     * @param other    the stream to pair with
     * @param combiner the function applied to each pair
     * @param <U>      the element type of the other stream
     * @param <R>      the combined type
     * @return the combined values
     * @see StreamUtils#zip(Stream, Stream, BiFunction)
     */
    public <U, R> Streamer<R> zip(Stream<U> other,
                                  BiFunction<? super T, ? super U, ? extends R> combiner) {
        return new Streamer<>(StreamUtils.zip(delegate, other, combiner));
    }

    /**
     * Pairs each element with its zero based position.
     *
     * @return index and element pairs
     * @see StreamUtils#zipWithIndex(Stream)
     */
    public Streamer<Pair<Integer, T>> zipWithIndex() {
        return new Streamer<>(StreamUtils.zipWithIndex(delegate));
    }

    /**
     * Groups consecutive elements into immutable lists of the given size.
     *
     * @param batchSize the number of elements per batch
     * @return the batches, the last one possibly shorter
     * @see StreamUtils#batch(Stream, int)
     */
    public Streamer<List<T>> batch(int batchSize) {
        return new Streamer<>(StreamUtils.batch(delegate, batchSize));
    }

    /**
     * Emits every sliding window of the given size, advancing one element at a time.
     *
     * @param size the window size
     * @return the windows
     * @see StreamUtils#windowed(Stream, int)
     */
    public Streamer<List<T>> windowed(int size) {
        return new Streamer<>(StreamUtils.windowed(delegate, size));
    }

    /**
     * Emits sliding windows of the given size, advancing by {@code step} elements.
     *
     * @param size the window size
     * @param step how far the window advances between emissions
     * @return the windows
     * @see StreamUtils#windowed(Stream, int, int)
     */
    public Streamer<List<T>> windowed(int size, int step) {
        return new Streamer<>(StreamUtils.windowed(delegate, size, step));
    }

    /**
     * Groups runs of adjacent elements that belong together.
     *
     * @param sameGroup tests whether two adjacent elements belong to the same group
     * @return the groups, each with at least one element
     * @see StreamUtils#groupAdjacent(Stream, BiPredicate)
     */
    public Streamer<List<T>> groupAdjacent(BiPredicate<? super T, ? super T> sameGroup) {
        return new Streamer<>(StreamUtils.groupAdjacent(delegate, sameGroup));
    }

    /**
     * Groups runs of adjacent elements that produce the same key.
     *
     * @param keyExtractor the function producing the grouping key
     * @param <K>          the key type
     * @return the groups, each with at least one element
     * @see StreamUtils#groupAdjacentBy(Stream, Function)
     */
    public <K> Streamer<List<T>> groupAdjacentBy(Function<? super T, ? extends K> keyExtractor) {
        return new Streamer<>(StreamUtils.groupAdjacentBy(delegate, keyExtractor));
    }

    /**
     * Cuts the stream into segments at the elements matching the delimiter.
     *
     * @param delimiter identifies the elements that separate segments
     * @return the segments, delimiters dropped
     * @see StreamUtils#splitBy(Stream, Predicate)
     */
    public Streamer<List<T>> splitBy(Predicate<? super T> delimiter) {
        return new Streamer<>(StreamUtils.splitBy(delegate, delimiter));
    }

    /**
     * Emits the running result of folding the stream from left to right.
     *
     * @param seed        the initial value, emitted first
     * @param accumulator combines the running value with the next element
     * @param <R>         the accumulated type
     * @return the running values, one more than the input has elements
     * @see StreamUtils#scan(Stream, Object, BiFunction)
     */
    public <R> Streamer<R> scan(R seed, BiFunction<? super R, ? super T, ? extends R> accumulator) {
        return new Streamer<>(StreamUtils.scan(delegate, seed, accumulator));
    }

    /**
     * Expands each element into a stream and pairs every produced value with its source element.
     *
     * @param mapper produces the values to pair with each element
     * @param <U>    the produced value type
     * @return source and value pairs
     * @see StreamUtils#flatMapToPair(Stream, Function)
     */
    public <U> Streamer<Pair<T, U>> flatMapToPair(Function<? super T, ? extends Stream<U>> mapper) {
        return new Streamer<>(StreamUtils.flatMapToPair(delegate, mapper));
    }

    /**
     * Alternates between this stream and the other, taking one element from each in turn.
     *
     * @param other the stream to alternate with
     * @return the interleaved elements
     * @see StreamUtils#interleave(Stream, Stream)
     */
    public Streamer<T> interleaveWith(Stream<? extends T> other) {
        return new Streamer<>(StreamUtils.interleave(delegate, other));
    }

    /**
     * Merges this stream with another one, both already sorted by the given comparator.
     *
     * @param other      the other sorted stream
     * @param comparator the ordering both streams follow
     * @return the merged elements, still sorted
     * @see StreamUtils#mergeSorted(Stream, Stream, Comparator)
     */
    public Streamer<T> mergeSortedWith(Stream<? extends T> other, Comparator<? super T> comparator) {
        return new Streamer<>(StreamUtils.mergeSorted(delegate, other, comparator));
    }

    /**
     * Appends another stream after this one.
     *
     * @param other the stream to append
     * @return the elements of both streams, in order
     * @see StreamUtils#concat(Stream[])
     */
    public Streamer<T> concatWith(Stream<? extends T> other) {
        return new Streamer<>(StreamUtils.concat(delegate, other));
    }

    /**
     * Applies a function to the whole stream, keeping the chain fluent.
     *
     * <p>The escape hatch for an operator this class does not have:</p>
     *
     * <pre>{@code
     * Streamer.of(source).pipe(s -> myLibrary.dedupe(s)).batch(100)
     * }</pre>
     *
     * @param transform the function to apply to this stream
     * @param <R>       the element type of the result
     * @return a fluent view of the transformed stream
     * @throws NullPointerException if {@code transform} is null, or it returns null
     */
    public <R> Streamer<R> pipe(Function<? super Stream<T>, ? extends Stream<R>> transform) {
        Objects.requireNonNull(transform, "transform");
        return of(Objects.requireNonNull(transform.apply(delegate), "transform returned null"));
    }

    /**
     * Returns the wrapped stream.
     *
     * @return the stream this instance delegates to
     */
    public Stream<T> unwrap() {
        return delegate;
    }

    // ---------------------------------------------------------------------
    // Stream, intermediate operations
    // ---------------------------------------------------------------------

    @Override
    public Streamer<T> filter(Predicate<? super T> predicate) {
        return new Streamer<>(delegate.filter(predicate));
    }

    @Override
    public <R> Streamer<R> map(Function<? super T, ? extends R> mapper) {
        return new Streamer<>(delegate.map(mapper));
    }

    @Override
    public IntStream mapToInt(ToIntFunction<? super T> mapper) {
        return delegate.mapToInt(mapper);
    }

    @Override
    public LongStream mapToLong(ToLongFunction<? super T> mapper) {
        return delegate.mapToLong(mapper);
    }

    @Override
    public DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper) {
        return delegate.mapToDouble(mapper);
    }

    @Override
    public <R> Streamer<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
        return new Streamer<>(delegate.flatMap(mapper));
    }

    @Override
    public IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper) {
        return delegate.flatMapToInt(mapper);
    }

    @Override
    public LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper) {
        return delegate.flatMapToLong(mapper);
    }

    @Override
    public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper) {
        return delegate.flatMapToDouble(mapper);
    }

    @Override
    public <R> Streamer<R> mapMulti(BiConsumer<? super T, ? super Consumer<R>> mapper) {
        return new Streamer<>(delegate.mapMulti(mapper));
    }

    @Override
    public IntStream mapMultiToInt(BiConsumer<? super T, ? super IntConsumer> mapper) {
        return delegate.mapMultiToInt(mapper);
    }

    @Override
    public LongStream mapMultiToLong(BiConsumer<? super T, ? super LongConsumer> mapper) {
        return delegate.mapMultiToLong(mapper);
    }

    @Override
    public DoubleStream mapMultiToDouble(BiConsumer<? super T, ? super DoubleConsumer> mapper) {
        return delegate.mapMultiToDouble(mapper);
    }

    @Override
    public Streamer<T> distinct() {
        return new Streamer<>(delegate.distinct());
    }

    @Override
    public Streamer<T> sorted() {
        return new Streamer<>(delegate.sorted());
    }

    @Override
    public Streamer<T> sorted(Comparator<? super T> comparator) {
        return new Streamer<>(delegate.sorted(comparator));
    }

    @Override
    public Streamer<T> peek(Consumer<? super T> action) {
        return new Streamer<>(delegate.peek(action));
    }

    @Override
    public Streamer<T> limit(long maxSize) {
        return new Streamer<>(delegate.limit(maxSize));
    }

    @Override
    public Streamer<T> skip(long n) {
        return new Streamer<>(delegate.skip(n));
    }

    @Override
    public Streamer<T> takeWhile(Predicate<? super T> predicate) {
        return new Streamer<>(delegate.takeWhile(predicate));
    }

    @Override
    public Streamer<T> dropWhile(Predicate<? super T> predicate) {
        return new Streamer<>(delegate.dropWhile(predicate));
    }

    // ---------------------------------------------------------------------
    // Stream, terminal operations
    // ---------------------------------------------------------------------

    @Override
    public void forEach(Consumer<? super T> action) {
        delegate.forEach(action);
    }

    @Override
    public void forEachOrdered(Consumer<? super T> action) {
        delegate.forEachOrdered(action);
    }

    @Override
    public Object[] toArray() {
        return delegate.toArray();
    }

    @Override
    public <A> A[] toArray(IntFunction<A[]> generator) {
        return delegate.toArray(generator);
    }

    @Override
    public T reduce(T identity, BinaryOperator<T> accumulator) {
        return delegate.reduce(identity, accumulator);
    }

    @Override
    public Optional<T> reduce(BinaryOperator<T> accumulator) {
        return delegate.reduce(accumulator);
    }

    @Override
    public <U> U reduce(U identity,
                        BiFunction<U, ? super T, U> accumulator,
                        BinaryOperator<U> combiner) {
        return delegate.reduce(identity, accumulator, combiner);
    }

    @Override
    public <R> R collect(Supplier<R> supplier,
                         BiConsumer<R, ? super T> accumulator,
                         BiConsumer<R, R> combiner) {
        return delegate.collect(supplier, accumulator, combiner);
    }

    @Override
    public <R, A> R collect(Collector<? super T, A, R> collector) {
        return delegate.collect(collector);
    }

    @Override
    public List<T> toList() {
        return delegate.toList();
    }

    @Override
    public Optional<T> min(Comparator<? super T> comparator) {
        return delegate.min(comparator);
    }

    @Override
    public Optional<T> max(Comparator<? super T> comparator) {
        return delegate.max(comparator);
    }

    @Override
    public long count() {
        return delegate.count();
    }

    @Override
    public boolean anyMatch(Predicate<? super T> predicate) {
        return delegate.anyMatch(predicate);
    }

    @Override
    public boolean allMatch(Predicate<? super T> predicate) {
        return delegate.allMatch(predicate);
    }

    @Override
    public boolean noneMatch(Predicate<? super T> predicate) {
        return delegate.noneMatch(predicate);
    }

    @Override
    public Optional<T> findFirst() {
        return delegate.findFirst();
    }

    @Override
    public Optional<T> findAny() {
        return delegate.findAny();
    }

    // ---------------------------------------------------------------------
    // BaseStream
    // ---------------------------------------------------------------------

    @Override
    public Iterator<T> iterator() {
        return delegate.iterator();
    }

    @Override
    public Spliterator<T> spliterator() {
        return delegate.spliterator();
    }

    @Override
    public boolean isParallel() {
        return delegate.isParallel();
    }

    @Override
    public Streamer<T> sequential() {
        return new Streamer<>(delegate.sequential());
    }

    @Override
    public Streamer<T> parallel() {
        return new Streamer<>(delegate.parallel());
    }

    @Override
    public Streamer<T> unordered() {
        return new Streamer<>(delegate.unordered());
    }

    @Override
    public Streamer<T> onClose(Runnable closeHandler) {
        return new Streamer<>(delegate.onClose(closeHandler));
    }

    @Override
    public void close() {
        delegate.close();
    }
}
