package org.streamer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class StreamerTest {

    @Nested
    @DisplayName("entry points")
    class EntryPoints {

        @Test
        void ofWrapsAnExistingStream() {
            assertThat(Streamer.of(Stream.of(1, 2, 3))).containsExactly(1, 2, 3);
        }

        @Test
        void ofDoesNotWrapAStreamerTwice() {
            Streamer<Integer> once = Streamer.of(1, 2);

            assertThat(Streamer.of((Stream<Integer>) once)).isSameAs(once);
        }

        @Test
        void ofTakesVarargs() {
            assertThat(Streamer.of("a", "b")).containsExactly("a", "b");
        }

        @Test
        void fromReadsIterablesAndIterators() {
            assertThat(Streamer.from(new ArrayDeque<>(List.of(1, 2)))).containsExactly(1, 2);
            assertThat(Streamer.from(List.of(3, 4).iterator())).containsExactly(3, 4);
        }

        @Test
        void emptyAndRangeStartAChain() {
            assertThat(Streamer.empty()).isEmpty();
            assertThat(Streamer.range(1, 4)).containsExactly(1, 2, 3);
        }

        @Test
        void entryPointsRejectNull() {
            assertThatNullPointerException().isThrownBy(() -> Streamer.of((Stream<String>) null));
            assertThatNullPointerException().isThrownBy(() -> Streamer.of((String[]) null));
            assertThatNullPointerException().isThrownBy(() -> Streamer.from((Iterable<String>) null));
        }
    }

    @Nested
    @DisplayName("operators")
    class Operators {

        @Test
        void theChainReadsInTheOrderTheDataFlows() {
            List<List<List<Integer>>> result = Streamer.range(1, 8)
                    .filterNot(value -> value == 4)
                    .windowed(3)
                    .batch(2)
                    .toList();

            // 1,2,3,5,6,7 -> windows [1,2,3] [2,3,5] [3,5,6] [5,6,7] -> batched in pairs
            assertThat(result).containsExactly(
                    List.of(List.of(1, 2, 3), List.of(2, 3, 5)),
                    List.of(List.of(3, 5, 6), List.of(5, 6, 7)));
        }

        @Test
        void everyStreamUtilsOperatorHasAFluentForm() {
            assertThat(Streamer.of(1, 2, 3, 4).filterNot(value -> value % 2 == 0))
                    .containsExactly(1, 3);
            assertThat(Streamer.<Object>of(1, "two", 3).filterByType(String.class))
                    .containsExactly("two");
            assertThat(Streamer.of("apple", "avocado", "beet").distinctBy(word -> word.charAt(0)))
                    .containsExactly("apple", "beet");
            assertThat(Streamer.of(1, 2, 3, 4).takeUntil(value -> value == 2))
                    .containsExactly(1, 2);
            assertThat(Streamer.of("a", "b").zip(Stream.of(1, 2)))
                    .containsExactly(Pair.of("a", 1), Pair.of("b", 2));
            assertThat(Streamer.of("a", "b").zip(Stream.of(1, 2), (s, i) -> s + i))
                    .containsExactly("a1", "b2");
            assertThat(Streamer.of("a", "b").zipWithIndex())
                    .containsExactly(Pair.of(0, "a"), Pair.of(1, "b"));
            assertThat(Streamer.of(1, 2, 3).batch(2))
                    .containsExactly(List.of(1, 2), List.of(3));
            assertThat(Streamer.of(1, 2, 3).windowed(2))
                    .containsExactly(List.of(1, 2), List.of(2, 3));
            assertThat(Streamer.of(1, 2, 3, 4).windowed(2, 2))
                    .containsExactly(List.of(1, 2), List.of(3, 4));
            assertThat(Streamer.of(1, 1, 2).groupAdjacent(Integer::equals))
                    .containsExactly(List.of(1, 1), List.of(2));
            assertThat(Streamer.of("ant", "ape", "bee").groupAdjacentBy(w -> w.charAt(0)))
                    .containsExactly(List.of("ant", "ape"), List.of("bee"));
            assertThat(Streamer.of(1, 0, 2).splitBy(value -> value == 0))
                    .containsExactly(List.of(1), List.of(2));
            assertThat(Streamer.of(1, 2, 3).scan(0, Integer::sum))
                    .containsExactly(0, 1, 3, 6);
            assertThat(Streamer.of("a").flatMapToPair(s -> Stream.of(1, 2)))
                    .containsExactly(Pair.of("a", 1), Pair.of("a", 2));
            assertThat(Streamer.of(1, 3).interleaveWith(Stream.of(2, 4)))
                    .containsExactly(1, 2, 3, 4);
            assertThat(Streamer.of(1, 4).mergeSortedWith(Stream.of(2, 3), Comparator.naturalOrder()))
                    .containsExactly(1, 2, 3, 4);
            assertThat(Streamer.of(1, 2).concatWith(Stream.of(3)))
                    .containsExactly(1, 2, 3);
        }

        @Test
        void pipeAppliesAnArbitraryTransformAndStaysFluent() {
            List<List<Integer>> result = Streamer.range(0, 6)
                    .pipe(stream -> stream.map(value -> value * 10))
                    .batch(3)
                    .toList();

            assertThat(result).containsExactly(List.of(0, 10, 20), List.of(30, 40, 50));
        }

        @Test
        void pipeRejectsNullTransformsAndNullResults() {
            assertThatNullPointerException().isThrownBy(() -> Streamer.of(1).pipe(null));
            assertThatNullPointerException()
                    .isThrownBy(() -> Streamer.of(1).pipe(stream -> null))
                    .withMessageContaining("transform returned null");
        }

        @Test
        void unwrapReturnsThePlainStream() {
            Stream<Integer> plain = Stream.of(1, 2);

            assertThat(Streamer.of(plain).unwrap()).isSameAs(plain);
        }
    }

    @Nested
    @DisplayName("Stream contract")
    class StreamContract {

        @Test
        void everyIntermediateOperationKeepsTheChainFluent() {
            // Each of these would return a plain Stream if the override were missing, and the next
            // call in the chain would not compile.
            List<String> result = Streamer.range(0, 20)
                    .filter(value -> value % 2 == 0)
                    .map(value -> value / 2)
                    .distinct()
                    .sorted()
                    .sorted(Comparator.reverseOrder())
                    .peek(value -> { })
                    .skip(1)
                    .limit(6)
                    .dropWhile(value -> value > 7)
                    .takeWhile(value -> value > 4)
                    .flatMap(Stream::of)
                    .<String>mapMulti((value, sink) -> sink.accept("v" + value))
                    .sequential()
                    .unordered()
                    .toList();

            assertThat(result).containsExactly("v7", "v6", "v5");
        }

        @Test
        void primitiveMappingsReturnPrimitiveStreams() {
            Streamer<String> words = Streamer.of("a", "bb", "ccc");

            assertThat(words.mapToInt(String::length).sum()).isEqualTo(6);
            assertThat(Streamer.of("a", "bb").mapToLong(String::length).sum()).isEqualTo(3);
            assertThat(Streamer.of("a", "bb").mapToDouble(String::length).sum()).isEqualTo(3.0);
            assertThat(Streamer.of("ab").flatMapToInt(String::chars).count()).isEqualTo(2);
            assertThat(Streamer.of(1L, 2L).flatMapToLong(java.util.stream.LongStream::of).sum())
                    .isEqualTo(3L);
            assertThat(Streamer.of(1.5).flatMapToDouble(java.util.stream.DoubleStream::of).sum())
                    .isEqualTo(1.5);
            assertThat(Streamer.of("ab").mapMultiToInt((s, sink) -> s.chars().forEach(sink)).count())
                    .isEqualTo(2);
            assertThat(Streamer.of(1L).mapMultiToLong((v, sink) -> sink.accept(v)).sum())
                    .isEqualTo(1L);
            assertThat(Streamer.of(2.5).mapMultiToDouble((v, sink) -> sink.accept(v)).sum())
                    .isEqualTo(2.5);
        }

        @Test
        void terminalOperationsDelegate() {
            assertThat(Streamer.of(1, 2, 3).count()).isEqualTo(3);
            assertThat(Streamer.of(1, 2, 3).reduce(0, Integer::sum)).isEqualTo(6);
            assertThat(Streamer.of(1, 2, 3).reduce(Integer::sum)).contains(6);
            assertThat(Streamer.of(1, 2, 3).reduce(0, Integer::sum, Integer::sum)).isEqualTo(6);
            assertThat(Streamer.of(1, 2, 3).collect(Collectors.toList())).containsExactly(1, 2, 3);
            StringBuilder appended = Streamer.of(1, 2)
                    .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append);
            assertThat(appended).hasToString("12");
            assertThat(Streamer.of(1, 2, 3).min(Comparator.naturalOrder())).contains(1);
            assertThat(Streamer.of(1, 2, 3).max(Comparator.naturalOrder())).contains(3);
            assertThat(Streamer.of(1, 2, 3).anyMatch(value -> value == 2)).isTrue();
            assertThat(Streamer.of(1, 2, 3).allMatch(value -> value > 0)).isTrue();
            assertThat(Streamer.of(1, 2, 3).noneMatch(value -> value > 5)).isTrue();
            assertThat(Streamer.of(1, 2, 3).findFirst()).contains(1);
            assertThat(Streamer.of(1, 2, 3).findAny()).isPresent();
            assertThat(Streamer.of(1, 2).toArray()).containsExactly(1, 2);
            assertThat(Streamer.of(1, 2).toArray(Integer[]::new)).containsExactly(1, 2);
            assertThat(Streamer.of(1, 2).iterator()).toIterable().containsExactly(1, 2);
            assertThat(Streamer.of(1, 2).spliterator().estimateSize()).isEqualTo(2);
        }

        @Test
        void forEachVariantsVisitEveryElement() {
            List<Integer> seen = new java.util.ArrayList<>();
            Streamer.of(1, 2).forEach(seen::add);
            Streamer.of(3, 4).forEachOrdered(seen::add);

            assertThat(seen).containsExactly(1, 2, 3, 4);
        }

        @Test
        void parallelismFlagsArePassedThrough() {
            assertThat(Streamer.of(1, 2).isParallel()).isFalse();
            assertThat(Streamer.of(1, 2).parallel().isParallel()).isTrue();
            assertThat(Streamer.of(1, 2).parallel().sequential().isParallel()).isFalse();
        }

        @Test
        void closeHandlersSurviveTheWholeChain() {
            AtomicInteger closed = new AtomicInteger();

            try (Streamer<List<Integer>> chain = Streamer.range(0, 6)
                    .onClose(closed::incrementAndGet)
                    .map(value -> value * 2)
                    .batch(2)) {
                assertThat(chain).hasSize(3);
            }

            assertThat(closed).hasValue(1);
        }

        @Test
        void aStreamerIsAcceptedWhereverAStreamIs() {
            Stream<Integer> asStream = Streamer.of(1, 2, 3);

            assertThat(StreamUtils.batch(asStream, 2)).containsExactly(List.of(1, 2), List.of(3));
        }

        @Test
        void everyStreamMethodIsOverridden() {
            // A method left to the interface default would silently return a plain Stream and
            // break the chain. This fails the moment a new JDK adds one.
            List<String> notOverridden = Arrays.stream(Stream.class.getMethods())
                    .filter(method -> !Modifier.isStatic(method.getModifiers()))
                    .filter(method -> !isDeclaredOn(method, Streamer.class))
                    .map(Method::getName)
                    .distinct()
                    .sorted()
                    .toList();

            assertThat(notOverridden).isEmpty();
        }

        private boolean isDeclaredOn(Method method, Class<?> type) {
            try {
                return type.getDeclaredMethod(method.getName(), method.getParameterTypes()) != null;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }
    }
}
