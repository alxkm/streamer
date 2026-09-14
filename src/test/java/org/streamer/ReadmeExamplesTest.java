package org.streamer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the snippets printed in README.md so the documentation cannot drift from the code.
 *
 * <p>Every assertion here mirrors a comment in the README, including the marble diagrams.</p>
 */
@DisplayName("README examples")
class ReadmeExamplesTest {

    @Test
    void sixtySecondTour() {
        assertThat(Streamer.of("ann", "bob").zip(Stream.of(91, 78)).toList())
                .hasToString("[Pair[first=ann, second=91], Pair[first=bob, second=78]]");

        assertThat(Streamer.of("alpha", "beta").zipWithIndex()
                .map(p -> (p.first() + 1) + ": " + p.second()))
                .containsExactly("1: alpha", "2: beta");

        assertThat(Streamer.of(10, 20, 30).scan(0, Integer::sum).toList())
                .isEqualTo(List.of(0, 10, 30, 60));

        assertThat(Streamer.of(1, 1, 2, 3, 3, 3).groupAdjacent(Integer::equals).toList())
                .isEqualTo(List.of(List.of(1, 1), List.of(2), List.of(3, 3, 3)));

        assertThat(Streamer.of("a", "c").interleaveWith(Stream.of("b", "d")).toList())
                .isEqualTo(List.of("a", "b", "c", "d"));

        Map<String, Long> counts =
                Stream.of("the", "and", "the").collect(MoreCollectors.toFrequencyMap());
        assertThat(counts).isEqualTo(Map.of("the", 2L, "and", 1L));

        Optional<Pair<Double, Double>> bounds = Stream.of(1.02, 50.0, 98.4)
                .collect(MoreCollectors.minMax(Comparator.naturalOrder()));
        assertThat(bounds).hasToString("Optional[Pair[first=1.02, second=98.4]]");
    }

    @Test
    void theTwoFormsBuildTheSamePipeline() {
        // "Both build identical pipelines. Streamer adds no state of its own; it delegates."
        assertThat(Streamer.of(1, 2, 3, 4, 5).batch(2).toList())
                .isEqualTo(StreamUtils.batch(Stream.of(1, 2, 3, 4, 5), 2).toList());
    }

    @Test
    void theFluentChainReadsInTheOrderTheDataFlows() {
        List<List<Integer>> result = Streamer.of(List.of(1, 2, 3, 4, 5).stream())
                .filter(value -> value != 3)
                .windowed(2)
                .batch(2)
                .toList()
                .stream()
                .flatMap(List::stream)
                .toList();

        assertThat(result).containsExactly(
                List.of(1, 2), List.of(2, 4), List.of(4, 5));
    }

    @Test
    void pipeIsTheEscapeHatchShownInTheTour() {
        assertThat(Streamer.of(1, 2, 3, 4)
                .pipe(stream -> stream.map(value -> value * 10))
                .batch(2))
                .containsExactly(List.of(10, 20), List.of(30, 40));
    }

    @Test
    void marbleDiagrams() {
        // zip: a, b, c  x  1, 2  ->  (a,1), (b,2)
        assertThat(StreamUtils.zip(Stream.of("a", "b", "c"), Stream.of(1, 2)))
                .containsExactly(Pair.of("a", 1), Pair.of("b", 2));

        // batch(s, 3): 1..8 -> [1,2,3] [4,5,6] [7,8]
        assertThat(StreamUtils.batch(StreamUtils.range(1, 9), 3))
                .containsExactly(List.of(1, 2, 3), List.of(4, 5, 6), List.of(7, 8));

        // windowed(s, 3): 1..5 -> [1,2,3] [2,3,4] [3,4,5]
        assertThat(StreamUtils.windowed(StreamUtils.range(1, 6), 3))
                .containsExactly(List.of(1, 2, 3), List.of(2, 3, 4), List.of(3, 4, 5));

        // scan(s, 0, +): 1, 2, 3 -> 0, 1, 3, 6
        assertThat(StreamUtils.scan(Stream.of(1, 2, 3), 0, Integer::sum))
                .containsExactly(0, 1, 3, 6);

        // groupAdjacent: a a b c c c -> [a,a] [b] [c,c,c]
        assertThat(StreamUtils.groupAdjacent(Stream.of("a", "a", "b", "c", "c", "c"), String::equals))
                .containsExactly(List.of("a", "a"), List.of("b"), List.of("c", "c", "c"));

        // mergeSorted: 1,4,7 + 2,3,8 -> 1,2,3,4,7,8
        assertThat(StreamUtils.mergeSorted(
                Stream.of(1, 4, 7), Stream.of(2, 3, 8), Comparator.<Integer>naturalOrder()))
                .containsExactly(1, 2, 3, 4, 7, 8);

        // interleave: 1,3,5 + 2,4 -> 1,2,3,4,5
        assertThat(StreamUtils.interleave(Stream.of(1, 3, 5), Stream.of(2, 4)))
                .containsExactly(1, 2, 3, 4, 5);

        // takeUntil(x == 3): 1..5 -> 1,2,3
        assertThat(StreamUtils.takeUntil(StreamUtils.range(1, 6), x -> x == 3))
                .containsExactly(1, 2, 3);
    }

    @Test
    void windowedComputesAMovingAverage() {
        List<Double> prices = List.of(10.0, 20.0, 30.0, 40.0);

        double[] movingAverage = Streamer.of(prices.stream())
                .windowed(3)
                .mapToDouble(w -> w.stream().mapToDouble(Double::doubleValue).average().orElseThrow())
                .toArray();

        assertThat(movingAverage).containsExactly(20.0, 30.0);
    }

    @Test
    void nullElementsSurviveMergeSortedWithANullFriendlyComparator() {
        assertThat(Streamer.of(null, "c")
                .mergeSortedWith(Stream.of("a", "b"),
                        Comparator.nullsFirst(Comparator.<String>naturalOrder())))
                .containsExactly(null, "a", "b", "c");
    }

    @Nested
    @DisplayName("recipes")
    class Recipes {

        @Test
        void bulkInsertKeepsOneBatchLiveAndClosesTheSource() {
            List<List<Integer>> inserted = new ArrayList<>();
            AtomicBoolean closed = new AtomicBoolean();

            try (Stream<Integer> rows = StreamUtils.range(0, 7).onClose(() -> closed.set(true))) {
                Streamer.of(rows).batch(3).forEach(inserted::add);
            }

            assertThat(inserted)
                    .containsExactly(List.of(0, 1, 2), List.of(3, 4, 5), List.of(6));
            assertThat(closed).isTrue();
        }

        @Test
        void recordsSeparatedByBlankLines() {
            Stream<String> lines =
                    Stream.of("name: ann", "age: 30", "", "", "name: bob", "age: 41", "");

            List<List<String>> records = Streamer.of(lines)
                    .splitBy(String::isBlank)
                    .filter(not(List::isEmpty))
                    .toList();

            assertThat(records).containsExactly(
                    List.of("name: ann", "age: 30"),
                    List.of("name: bob", "age: 41"));
        }

        @Test
        void collapseALogIntoRunsOfTheSameLevel() {
            record Event(String level, String message) { }
            Stream<Event> events = Stream.of(
                    new Event("INFO", "started"),
                    new Event("WARN", "retrying"),
                    new Event("WARN", "retrying"),
                    new Event("WARN", "retrying"),
                    new Event("INFO", "done"));

            List<String> collapsed = Streamer.of(events)
                    .groupAdjacentBy(Event::level)
                    .map(run -> run.size() == 1
                            ? run.get(0).message()
                            : run.size() + "x " + run.get(0).message())
                    .toList();

            assertThat(collapsed).containsExactly("started", "3x retrying", "done");
        }

        @Test
        void runningBalanceFromALedger() {
            List<Integer> entries = List.of(100, -30, -20, 50);

            List<Integer> balances = Streamer.of(entries.stream())
                    .scan(0, Integer::sum)
                    .toList();

            assertThat(balances).containsExactly(0, 100, 70, 50, 100);
        }

        @Test
        void mergeTwoSortedExports() {
            Stream<String> left = Stream.of("2024-01-01", "2024-03-01");
            Stream<String> right = Stream.of("2024-02-01", "2024-04-01");

            assertThat(Streamer.of(left).mergeSortedWith(right, Comparator.naturalOrder()))
                    .containsExactly("2024-01-01", "2024-02-01", "2024-03-01", "2024-04-01");
        }

        @Test
        void consumeAPaginatedApiUntilTheLastPage() {
            record Page(int number, boolean isLast, List<String> items) { }
            AtomicInteger requested = new AtomicInteger();

            Stream<Page> pages = Stream.iterate(
                    new Page(0, false, List.of("a")),
                    page -> {
                        requested.incrementAndGet();
                        int next = page.number() + 1;
                        return new Page(next, next == 2, List.of("page" + next));
                    });

            List<String> items = Streamer.of(pages)
                    .takeUntil(Page::isLast)
                    .flatMap(page -> page.items().stream())
                    .toList();

            assertThat(items).containsExactly("a", "page1", "page2");
            // Page 3 is never built, let alone fetched.
            assertThat(requested).hasValue(2);
        }

        @Test
        void numberTheLinesOfAFile() {
            Stream<String> lines = Stream.of("first", "second", "third");

            String numbered = Streamer.of(lines)
                    .zipWithIndex()
                    .map(p -> (p.first() + 1) + " | " + p.second())
                    .collect(Collectors.joining("\n"));

            assertThat(numbered).isEqualTo("1 | first\n2 | second\n3 | third");
        }

        @Test
        void firstEntryPerKeyKeepingEncounterOrder() {
            record Person(String name, String city) { }
            List<Person> people = List.of(
                    new Person("ann", "berlin"),
                    new Person("bob", "lisbon"),
                    new Person("cat", "berlin"));

            List<String> onePerCity = Streamer.of(people.stream())
                    .distinctBy(Person::city)
                    .map(Person::name)
                    .toList();

            assertThat(onePerCity).containsExactly("ann", "bob");
        }
    }

    @Test
    void batchingReleasesTheSourceOnClose() {
        List<List<Integer>> inserted;
        try (Stream<Integer> rows = StreamUtils.range(0, 5)) {
            inserted = StreamUtils.batch(rows, 2).toList();
        }

        assertThat(inserted)
                .containsExactly(List.of(0, 1), List.of(2, 3), List.of(4));
    }
}
