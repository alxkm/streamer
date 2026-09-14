package org.streamer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StreamUtilsTest {

    @Nested
    @DisplayName("creation")
    class Creation {

        @Test
        void asStreamReadsAnIterator() {
            Iterator<String> iterator = List.of("a", "b", "c").iterator();

            assertThat(StreamUtils.asStream(iterator)).containsExactly("a", "b", "c");
        }

        @Test
        void asStreamOfAnExhaustedIteratorIsEmpty() {
            assertThat(StreamUtils.asStream(Collections.emptyIterator())).isEmpty();
        }

        @Test
        void asStreamReadsAnIterable() {
            Iterable<String> iterable = new ArrayDeque<>(List.of("a", "b"));

            assertThat(StreamUtils.asStream(iterable)).containsExactly("a", "b");
        }

        @Test
        void asStreamReadsAnEnumeration() {
            Enumeration<String> enumeration = new Vector<>(List.of("a", "b")).elements();

            assertThat(StreamUtils.asStream(enumeration)).containsExactly("a", "b");
        }

        @Test
        void asStreamIsLazy() {
            AtomicInteger pulled = new AtomicInteger();
            Iterator<Integer> counting = new Iterator<>() {
                private int next;

                @Override
                public boolean hasNext() {
                    return true;
                }

                @Override
                public Integer next() {
                    pulled.incrementAndGet();
                    return next++;
                }
            };

            List<Integer> firstThree = StreamUtils.asStream(counting).limit(3).toList();

            assertThat(firstThree).containsExactly(0, 1, 2);
            assertThat(pulled).hasValueLessThan(10);
        }

        @Test
        void ofNullableWrapsAValue() {
            assertThat(StreamUtils.ofNullable("a")).containsExactly("a");
        }

        @Test
        void ofNullableOfNullIsEmpty() {
            assertThat(StreamUtils.ofNullable(null)).isEmpty();
        }

        @Test
        void rangeCoversTheHalfOpenInterval() {
            assertThat(StreamUtils.range(1, 6)).containsExactly(1, 2, 3, 4, 5);
        }

        @Test
        void rangeIsEmptyWhenInvertedOrDegenerate() {
            assertThat(StreamUtils.range(5, 5)).isEmpty();
            assertThat(StreamUtils.range(5, 1)).isEmpty();
        }

        @Test
        void rangeKeepsEveryValueWhenSplitInParallel() {
            List<Integer> parallel = StreamUtils.range(0, 10_000).parallel().toList();

            assertThat(parallel).hasSize(10_000).isSorted();
            assertThat(parallel.get(0)).isZero();
            assertThat(parallel.get(9_999)).isEqualTo(9_999);
        }

        @Test
        void rangeReportsItsExactSize() {
            assertThat(StreamUtils.range(3, 9).spliterator().getExactSizeIfKnown()).isEqualTo(6);
        }

        @Test
        void concatKeepsArgumentOrder() {
            Stream<String> result =
                    StreamUtils.concat(Stream.of("a", "b"), Stream.empty(), Stream.of("c"));

            assertThat(result).containsExactly("a", "b", "c");
        }

        @Test
        void concatOfNothingIsEmpty() {
            assertThat(StreamUtils.<String>concat()).isEmpty();
        }

        @Test
        void concatRejectsANullStreamUpFront() {
            assertThatNullPointerException()
                    .isThrownBy(() -> StreamUtils.concat(Stream.of("a"), null))
                    .withMessageContaining("null element");
        }
    }

    @Nested
    @DisplayName("filtering")
    class Filtering {

        @Test
        void filterByTypeKeepsAndCastsMatchingElements() {
            Stream<Object> mixed = Stream.of(1, "two", 3, "four");

            assertThat(StreamUtils.filterByType(mixed, String.class)).containsExactly("two", "four");
        }

        @Test
        void filterByTypeMatchesSubtypes() {
            Stream<Object> numbers = Stream.of(1, 2L, 3.0, "four");

            assertThat(StreamUtils.filterByType(numbers, Number.class)).containsExactly(1, 2L, 3.0);
        }

        @Test
        void filterNotIsTheComplementOfFilter() {
            assertThat(StreamUtils.filterNot(Stream.of(1, 2, 3, 4, 5), even()))
                    .containsExactly(1, 3, 5);
        }

        @Test
        void distinctByKeepsTheFirstElementPerKey() {
            Stream<String> fruit = Stream.of("apple", "avocado", "banana", "blueberry");

            assertThat(StreamUtils.distinctBy(fruit, s -> s.charAt(0)))
                    .containsExactly("apple", "banana");
        }

        @Test
        void distinctByKeyIsUsableAsAPlainPredicate() {
            List<String> result = Stream.of("apple", "banana", "apricot", "cherry")
                    .filter(StreamUtils.distinctByKey(s -> s.charAt(0)))
                    .toList();

            assertThat(result).containsExactly("apple", "banana", "cherry");
        }

        @Test
        void distinctByKeyIsSafeOnAParallelStream() {
            List<Integer> result = IntStream.range(0, 10_000)
                    .boxed()
                    .parallel()
                    .filter(StreamUtils.distinctByKey(i -> i % 100))
                    .toList();

            assertThat(result).hasSize(100);
            assertThat(result.stream().map(i -> i % 100).collect(Collectors.toSet())).hasSize(100);
        }

        @Test
        void takeUntilIncludesTheMatchingElement() {
            assertThat(StreamUtils.takeUntil(Stream.of(1, 2, 3, 4, 5), x -> x == 3))
                    .containsExactly(1, 2, 3);
        }

        @Test
        void takeUntilWithoutAMatchKeepsEverything() {
            assertThat(StreamUtils.takeUntil(Stream.of(1, 2, 3), x -> x > 10))
                    .containsExactly(1, 2, 3);
        }

        @Test
        void takeUntilAcceptsASortedSource() {
            // Regression: the wrapper used to inherit SORTED from the source without being able to
            // answer getComparator(), which the pipeline calls whenever SORTED is reported.
            assertThat(StreamUtils.takeUntil(StreamUtils.range(1, 10), x -> x == 3))
                    .containsExactly(1, 2, 3);
        }

        @Test
        void takeUntilStopsPullingAfterTheMatch() {
            AtomicInteger pulled = new AtomicInteger();
            Stream<Integer> counted = Stream.iterate(1, x -> x + 1).peek(x -> pulled.incrementAndGet());

            assertThat(StreamUtils.takeUntil(counted, x -> x == 3)).containsExactly(1, 2, 3);
            assertThat(pulled).hasValue(3);
        }
    }

    @Nested
    @DisplayName("reshaping")
    class Reshaping {

        @Test
        void zipPairsElementsPositionally() {
            Stream<String> letters = Stream.of("a", "b");
            Stream<Integer> numbers = Stream.of(1, 2);

            assertThat(StreamUtils.zip(letters, numbers))
                    .containsExactly(Pair.of("a", 1), Pair.of("b", 2));
        }

        @Test
        void zipStopsAtTheShorterInput() {
            Stream<String> letters = Stream.of("a", "b", "c");
            Stream<Integer> numbers = Stream.of(1);

            assertThat(StreamUtils.zip(letters, numbers)).containsExactly(Pair.of("a", 1));
        }

        @Test
        void zipDoesNotPullPastTheShorterInput() {
            AtomicInteger pulled = new AtomicInteger();
            Stream<Integer> endless = Stream.iterate(1, x -> x + 1).peek(x -> pulled.incrementAndGet());

            assertThat(StreamUtils.zip(Stream.of("a"), endless)).hasSize(1);
            assertThat(pulled).hasValue(1);
        }

        @Test
        void zipWithACombinerAppliesIt() {
            Stream<String> names = Stream.of("ann", "bob");
            Stream<Integer> scores = Stream.of(7, 9);

            assertThat(StreamUtils.zip(names, scores, (name, score) -> name + "=" + score))
                    .containsExactly("ann=7", "bob=9");
        }

        @Test
        void zipOfAnEmptyStreamIsEmpty() {
            assertThat(StreamUtils.zip(Stream.<String>empty(), Stream.of(1))).isEmpty();
            assertThat(StreamUtils.zip(Stream.of(1), Stream.<String>empty())).isEmpty();
        }

        @Test
        void zipWithIndexNumbersFromZero() {
            assertThat(StreamUtils.zipWithIndex(Stream.of("a", "b", "c")))
                    .containsExactly(Pair.of(0, "a"), Pair.of(1, "b"), Pair.of(2, "c"));
        }

        @Test
        void zipWithIndexStaysCorrectOnAParallelSource() {
            List<Pair<Integer, Integer>> indexed =
                    StreamUtils.zipWithIndex(IntStream.range(0, 1_000).boxed().parallel()).toList();

            assertThat(indexed).hasSize(1_000);
            assertThat(indexed).allSatisfy(pair -> assertThat(pair.first()).isEqualTo(pair.second()));
        }

        @Test
        void batchSplitsIntoFixedSizeChunks() {
            assertThat(StreamUtils.batch(Stream.of(1, 2, 3, 4, 5, 6), 2))
                    .containsExactly(List.of(1, 2), List.of(3, 4), List.of(5, 6));
        }

        @Test
        void batchKeepsAShorterTail() {
            assertThat(StreamUtils.batch(Stream.of(1, 2, 3, 4, 5), 2))
                    .containsExactly(List.of(1, 2), List.of(3, 4), List.of(5));
        }

        @Test
        void batchOfAnEmptyStreamIsEmpty() {
            assertThat(StreamUtils.batch(Stream.empty(), 3)).isEmpty();
        }

        @Test
        void batchesAreImmutable() {
            List<Integer> batch = StreamUtils.batch(Stream.of(1, 2), 2).findFirst().orElseThrow();

            assertThatThrownBy(() -> batch.add(3)).isInstanceOf(UnsupportedOperationException.class);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
        void batchRejectsANonPositiveSize(int batchSize) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> StreamUtils.batch(Stream.of(1), batchSize))
                    .withMessageContaining("batchSize must be at least 1");
        }

        @Test
        void windowedSlidesOneElementAtATime() {
            assertThat(StreamUtils.windowed(Stream.of(1, 2, 3, 4), 2))
                    .containsExactly(List.of(1, 2), List.of(2, 3), List.of(3, 4));
        }

        @Test
        void windowedHonoursTheStep() {
            assertThat(StreamUtils.windowed(Stream.of(1, 2, 3, 4, 5, 6), 2, 3))
                    .containsExactly(List.of(1, 2), List.of(4, 5));
        }

        @Test
        void windowedDropsAnIncompleteTail() {
            assertThat(StreamUtils.windowed(Stream.of(1, 2, 3, 4, 5), 2, 2))
                    .containsExactly(List.of(1, 2), List.of(3, 4));
        }

        @Test
        void windowedSkipsElementsWhenTheStepExceedsTheWindow() {
            assertThat(StreamUtils.windowed(StreamUtils.range(1, 11), 2, 5))
                    .containsExactly(List.of(1, 2), List.of(6, 7));
        }

        @Test
        void windowedIsEmptyWhenTheSourceIsShorterThanTheWindow() {
            assertThat(StreamUtils.windowed(Stream.of(1, 2), 5)).isEmpty();
        }

        @Test
        void windowedRejectsNonPositiveArguments() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> StreamUtils.windowed(Stream.of(1), 0));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> StreamUtils.windowed(Stream.of(1), 2, 0));
        }

        @Test
        void groupAdjacentSplitsOnTheBoundary() {
            Stream<Integer> values = Stream.of(1, 1, 2, 2, 2, 3, 1);

            assertThat(StreamUtils.groupAdjacent(values, Integer::equals))
                    .containsExactly(List.of(1, 1), List.of(2, 2, 2), List.of(3), List.of(1));
        }

        @Test
        void groupAdjacentOfASingleElementYieldsOneGroup() {
            assertThat(StreamUtils.groupAdjacent(Stream.of("a"), String::equals))
                    .containsExactly(List.of("a"));
        }

        @Test
        void groupAdjacentOfAnEmptyStreamIsEmpty() {
            assertThat(StreamUtils.groupAdjacent(Stream.<String>empty(), String::equals)).isEmpty();
        }

        @Test
        void groupAdjacentByGroupsRunsWithTheSameKey() {
            Stream<String> words = Stream.of("ant", "ape", "bee", "cow", "cat", "ant");

            assertThat(StreamUtils.groupAdjacentBy(words, word -> word.charAt(0)))
                    .containsExactly(
                            List.of("ant", "ape"), List.of("bee"),
                            List.of("cow", "cat"), List.of("ant"));
        }

        @Test
        void groupAdjacentByTreatsNullKeysAsEqual() {
            Stream<String> words = Stream.of("aa", "ab", "b");

            assertThat(StreamUtils.groupAdjacentBy(words, word -> null))
                    .containsExactly(List.of("aa", "ab", "b"));
        }

        @Test
        void groupAdjacentByComputesEachKeyOnce() {
            AtomicInteger calls = new AtomicInteger();

            List<List<Integer>> groups = StreamUtils
                    .groupAdjacentBy(StreamUtils.range(0, 100), value -> {
                        calls.incrementAndGet();
                        return value / 10;
                    })
                    .toList();

            assertThat(groups).hasSize(10);
            assertThat(calls).hasValue(100);
        }

        @Test
        void groupAdjacentByOfAnEmptyStreamIsEmpty() {
            assertThat(StreamUtils.groupAdjacentBy(Stream.<String>empty(), String::length)).isEmpty();
        }

        @Test
        void splitByCutsAtTheDelimiterAndDropsIt() {
            Stream<String> lines = Stream.of("a", "b", "", "c", "", "d", "e");

            assertThat(StreamUtils.splitBy(lines, String::isEmpty))
                    .containsExactly(List.of("a", "b"), List.of("c"), List.of("d", "e"));
        }

        @Test
        void splitByKeepsEmptySegmentsBetweenConsecutiveDelimiters() {
            assertThat(StreamUtils.splitBy(Stream.of(1, 0, 0, 2), value -> value == 0))
                    .containsExactly(List.of(1), List.of(), List.of(2));
        }

        @Test
        void splitByEmitsALeadingEmptySegmentWhenTheStreamStartsWithADelimiter() {
            assertThat(StreamUtils.splitBy(Stream.of(0, 1), value -> value == 0))
                    .containsExactly(List.of(), List.of(1));
        }

        @Test
        void splitByAddsNoTrailingSegmentWhenTheStreamEndsOnADelimiter() {
            assertThat(StreamUtils.splitBy(Stream.of(1, 2, 0), value -> value == 0))
                    .containsExactly(List.of(1, 2));
        }

        @Test
        void splitByWithoutAnyDelimiterYieldsOneSegment() {
            assertThat(StreamUtils.splitBy(Stream.of(1, 2, 3), value -> value == 9))
                    .containsExactly(List.of(1, 2, 3));
        }

        @Test
        void splitByOfAnEmptyStreamIsEmpty() {
            assertThat(StreamUtils.splitBy(Stream.<Integer>empty(), value -> true)).isEmpty();
        }

        @Test
        void splitSegmentsAreImmutable() {
            List<Integer> segment =
                    StreamUtils.splitBy(Stream.of(1, 2), value -> false).findFirst().orElseThrow();

            assertThatThrownBy(() -> segment.add(3))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void scanEmitsTheSeedThenEveryRunningValue() {
            assertThat(StreamUtils.scan(Stream.of(1, 2, 3), 0, Integer::sum))
                    .containsExactly(0, 1, 3, 6);
        }

        @Test
        void scanOfAnEmptyStreamEmitsOnlyTheSeed() {
            assertThat(StreamUtils.scan(Stream.<Integer>empty(), 42, Integer::sum))
                    .containsExactly(42);
        }

        @Test
        void scanCanChangeTheElementType() {
            assertThat(StreamUtils.scan(Stream.of("a", "b"), "", String::concat))
                    .containsExactly("", "a", "ab");
        }

        @Test
        void flatMapToPairCarriesTheSourceElement() {
            Stream<String> letters = Stream.of("a", "b");

            assertThat(StreamUtils.flatMapToPair(letters, s -> Stream.of(1, 2)))
                    .containsExactly(
                            Pair.of("a", 1), Pair.of("a", 2),
                            Pair.of("b", 1), Pair.of("b", 2));
        }
    }

    @Nested
    @DisplayName("merging")
    class Merging {

        @Test
        void interleaveAlternatesBetweenBothStreams() {
            assertThat(StreamUtils.interleave(Stream.of(1, 3, 5), Stream.of(2, 4, 6)))
                    .containsExactly(1, 2, 3, 4, 5, 6);
        }

        @Test
        void interleaveAppendsTheRemainderOfTheLongerStream() {
            assertThat(StreamUtils.interleave(Stream.of(1), Stream.of(2, 4, 6)))
                    .containsExactly(1, 2, 4, 6);
            assertThat(StreamUtils.interleave(Stream.of(1, 3, 5), Stream.of(2)))
                    .containsExactly(1, 2, 3, 5);
        }

        @Test
        void interleaveOfTwoEmptyStreamsIsEmpty() {
            assertThat(StreamUtils.interleave(Stream.empty(), Stream.empty())).isEmpty();
        }

        @Test
        void interleaveRejectsReadingPastTheEnd() {
            Iterator<Integer> iterator =
                    StreamUtils.interleave(Stream.of(1), Stream.<Integer>empty()).iterator();
            iterator.next();

            assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void mergeSortedProducesASortedStream() {
            Stream<Integer> left = Stream.of(1, 4, 7);
            Stream<Integer> right = Stream.of(2, 3, 8);

            assertThat(StreamUtils.mergeSorted(left, right, Comparator.naturalOrder()))
                    .containsExactly(1, 2, 3, 4, 7, 8);
        }

        @Test
        void mergeSortedHandlesAnEmptySide() {
            assertThat(StreamUtils.mergeSorted(Stream.of(1, 2), Stream.empty(), Comparator.<Integer>naturalOrder()))
                    .containsExactly(1, 2);
            assertThat(StreamUtils.mergeSorted(Stream.empty(), Stream.of(1, 2), Comparator.<Integer>naturalOrder()))
                    .containsExactly(1, 2);
        }

        @Test
        void mergeSortedIsStableOnTies() {
            record Item(String source, int key) { }
            Stream<Item> left = Stream.of(new Item("L", 1), new Item("L", 2));
            Stream<Item> right = Stream.of(new Item("R", 1), new Item("R", 2));

            List<String> sources = StreamUtils
                    .mergeSorted(left, right, Comparator.comparingInt(Item::key))
                    .map(Item::source)
                    .toList();

            assertThat(sources).containsExactly("L", "R", "L", "R");
        }

        @Test
        void mergeSortedAcceptsNullElementsWhenTheComparatorDoes() {
            Stream<String> left = Stream.of(null, "c");
            Stream<String> right = Stream.of("a", "b");

            assertThat(StreamUtils.mergeSorted(
                    left, right, Comparator.nullsFirst(Comparator.<String>naturalOrder())))
                    .containsExactly(null, "a", "b", "c");
        }

        @Test
        void mergeSortedOfTwoEmptyStreamsIsEmpty() {
            assertThat(StreamUtils.mergeSorted(
                    Stream.empty(), Stream.empty(), Comparator.<Integer>naturalOrder())).isEmpty();
        }

        @Test
        void mergeSortedRejectsReadingPastTheEnd() {
            Iterator<Integer> iterator = StreamUtils
                    .mergeSorted(Stream.of(1), Stream.<Integer>empty(), Comparator.<Integer>naturalOrder())
                    .iterator();
            iterator.next();

            assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("conversion")
    class Conversion {

        @Test
        void arrayToCollectionFillsTheSuppliedCollection() {
            TreeSet<String> sorted =
                    StreamUtils.arrayToCollection(TreeSet::new, new String[] {"c", "a", "b"});

            assertThat(sorted).containsExactly("a", "b", "c");
        }

        @Test
        void arrayToCollectionKeepsDuplicatesInAList() {
            LinkedList<Integer> list =
                    StreamUtils.arrayToCollection(LinkedList::new, new Integer[] {1, 1, 2});

            assertThat(list).containsExactly(1, 1, 2);
        }

        @Test
        void arrayToCollectionRejectsAFactoryReturningNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> StreamUtils.arrayToCollection(() -> null, new String[0]))
                    .withMessageContaining("factory returned null");
        }

        @Test
        void reflectiveArrayToCollectionBuildsTheRequestedType() {
            Collection<String> list =
                    StreamUtils.arrayToCollection(ArrayList.class, new String[] {"a", "b"});

            assertThat(list).isInstanceOf(ArrayList.class).containsExactly("a", "b");
        }

        @Test
        void reflectiveArrayToCollectionDeduplicatesIntoASet() {
            Collection<Integer> set =
                    StreamUtils.arrayToCollection(java.util.HashSet.class, new Integer[] {1, 2, 2});

            assertThat(set).isInstanceOf(Set.class).containsExactlyInAnyOrder(1, 2);
        }

        @Test
        void reflectiveArrayToCollectionReportsAnUninstantiableType() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> StreamUtils.arrayToCollection(Collection.class, new String[0]))
                    .withMessageContaining("public no-argument constructor")
                    .withCauseInstanceOf(NoSuchMethodException.class);
        }
    }

    @Nested
    @DisplayName("contracts")
    class Contracts {

        @Test
        void theClassCannotBeInstantiated() throws Exception {
            var constructor = StreamUtils.class.getDeclaredConstructor();
            constructor.setAccessible(true);

            assertThatThrownBy(constructor::newInstance).hasRootCauseInstanceOf(AssertionError.class);
        }

        @Test
        void everyOperatorRejectsNullArguments() {
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.asStream((Iterator<?>) null));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.asStream((Iterable<?>) null));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.asStream((Enumeration<?>) null));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.filterByType(null, String.class));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.filterByType(Stream.empty(), null));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.filterNot(null, even()));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.filterNot(Stream.of(1), null));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.distinctBy(null, x -> x));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.distinctByKey(null));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.takeUntil(null, even()));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.takeUntil(Stream.of(1), null));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.zip(null, Stream.empty()));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.zip(Stream.empty(), null));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.zip(Stream.empty(), Stream.empty(), null));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.zipWithIndex(null));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.batch(null, 1));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.windowed(null, 1));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.groupAdjacent(null, Object::equals));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.groupAdjacent(Stream.of(1), null));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.groupAdjacentBy(null, x -> x));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.groupAdjacentBy(Stream.of(1), null));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.splitBy(null, x -> true));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.splitBy(Stream.of(1), null));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.scan(null, 0, Integer::sum));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.scan(Stream.of(1), 0, null));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.flatMapToPair(null, Stream::of));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.flatMapToPair(Stream.of(1), null));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.interleave(null, Stream.empty()));
            assertThatNullPointerException().isThrownBy(() -> StreamUtils.interleave(Stream.empty(), null));
            assertThatNullPointerException().isThrownBy(() ->
                    StreamUtils.mergeSorted(null, Stream.empty(), Comparator.<Integer>naturalOrder()));
            assertThatNullPointerException().isThrownBy(() ->
                    StreamUtils.mergeSorted(Stream.empty(), null, Comparator.<Integer>naturalOrder()));
            assertThatNullPointerException().isThrownBy(() ->
                    StreamUtils.mergeSorted(Stream.empty(), Stream.empty(), null));
            assertThatNullPointerException().isThrownBy(() ->
                    StreamUtils.arrayToCollection((java.util.function.Supplier<List<String>>) null,
                            new String[0]));
            assertThatNullPointerException().isThrownBy(() ->
                    StreamUtils.arrayToCollection(ArrayList::new, null));
            assertThatNullPointerException().isThrownBy(() ->
                    StreamUtils.arrayToCollection((Class<Collection>) null, new String[0]));
        }

        @Test
        void derivedStreamsCloseTheirSource() {
            AtomicInteger closed = new AtomicInteger();
            Stream<Integer> source = Stream.of(1, 2, 3, 4).onClose(closed::incrementAndGet);

            try (Stream<List<Integer>> batched = StreamUtils.batch(source, 2)) {
                assertThat(batched).hasSize(2);
            }

            assertThat(closed).hasValue(1);
        }

        @Test
        void zipClosesBothSources() {
            AtomicInteger closed = new AtomicInteger();
            Stream<Integer> left = Stream.of(1).onClose(closed::incrementAndGet);
            Stream<Integer> right = Stream.of(2).onClose(closed::incrementAndGet);

            try (Stream<Pair<Integer, Integer>> zipped = StreamUtils.zip(left, right)) {
                assertThat(zipped).hasSize(1);
            }

            assertThat(closed).hasValue(2);
        }

        @Test
        void operatorsChainTogether() {
            List<String> result = StreamUtils.zip(
                            StreamUtils.range(0, 6),
                            Stream.of("a", "b", "c", "d", "e", "f"),
                            (index, letter) -> index + letter)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toList(),
                            list -> StreamUtils.batch(list.stream(), 2).map(Object::toString).toList()));

            assertThat(result).containsExactly("[0a, 1b]", "[2c, 3d]", "[4e, 5f]");
        }
    }

    private static Predicate<Integer> even() {
        return value -> value % 2 == 0;
    }
}
