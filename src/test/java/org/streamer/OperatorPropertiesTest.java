package org.streamer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the invariants of each operator against randomly sized inputs.
 *
 * <p>Hand written cases pin down the examples someone thought of; these pin down the ones nobody
 * did. Every test is driven by a fixed list of seeds, so a failure reproduces exactly: the seed is
 * printed as the parameter of the failing invocation.</p>
 */
@DisplayName("operator invariants over random inputs")
class OperatorPropertiesTest {

    private static final int ROUNDS = 200;
    private static final int MAX_ELEMENTS = 60;

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1L, 42L, 1_337L, 20_240_917L, Long.MAX_VALUE})
    void batchPartitionsTheInputWithoutLosingOrDuplicatingAnything(long seed) {
        Random random = new Random(seed);
        for (int round = 0; round < ROUNDS; round++) {
            List<Integer> input = randomInput(random);
            int size = 1 + random.nextInt(MAX_ELEMENTS);

            List<List<Integer>> batches = StreamUtils.batch(input.stream(), size).toList();

            assertThat(flatten(batches))
                    .as("batch(%d) over %d elements must preserve the input", size, input.size())
                    .isEqualTo(input);
            assertThat(batches).hasSize((input.size() + size - 1) / size);
            if (!batches.isEmpty()) {
                assertThat(batches.subList(0, batches.size() - 1))
                        .allSatisfy(batch -> assertThat(batch).hasSize(size));
                assertThat(batches.get(batches.size() - 1).size()).isBetween(1, size);
            }
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {2L, 99L, 5_150L, 20_240_918L})
    void everyWindowIsTheMatchingSliceOfTheInput(long seed) {
        Random random = new Random(seed);
        for (int round = 0; round < ROUNDS; round++) {
            List<Integer> input = randomInput(random);
            int size = 1 + random.nextInt(MAX_ELEMENTS);
            int step = 1 + random.nextInt(MAX_ELEMENTS);

            List<List<Integer>> windows =
                    StreamUtils.windowed(input.stream(), size, step).toList();

            int expected = input.size() < size ? 0 : (input.size() - size) / step + 1;
            assertThat(windows)
                    .as("windowed(%d, %d) over %d elements", size, step, input.size())
                    .hasSize(expected);
            for (int i = 0; i < windows.size(); i++) {
                int from = i * step;
                assertThat(windows.get(i)).isEqualTo(input.subList(from, from + size));
            }
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {3L, 7L, 2_718L})
    void groupAdjacentPartitionsIntoMaximalRuns(long seed) {
        Random random = new Random(seed);
        for (int round = 0; round < ROUNDS; round++) {
            // A small alphabet makes adjacent duplicates likely, which is what this exercises.
            List<Integer> input = random.ints(random.nextInt(MAX_ELEMENTS), 0, 4)
                    .boxed()
                    .toList();

            List<List<Integer>> groups =
                    StreamUtils.groupAdjacent(input.stream(), Integer::equals).toList();

            assertThat(flatten(groups)).isEqualTo(input);
            assertThat(groups).allSatisfy(group -> {
                assertThat(group).isNotEmpty();
                assertThat(group).containsOnly(group.get(0));
            });
            // Maximal runs: no two consecutive groups may share a value, or they would be one run.
            for (int i = 1; i < groups.size(); i++) {
                assertThat(groups.get(i).get(0)).isNotEqualTo(groups.get(i - 1).get(0));
            }
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {21L, 43L, 60_221L})
    void splitByPartitionsTheNonDelimiterElements(long seed) {
        Random random = new Random(seed);
        for (int round = 0; round < ROUNDS; round++) {
            List<Integer> input = random.ints(random.nextInt(MAX_ELEMENTS), 0, 5).boxed().toList();

            List<List<Integer>> segments =
                    StreamUtils.splitBy(input.stream(), value -> value == 0).toList();

            // Every non delimiter element survives, in order, and no delimiter does.
            assertThat(flatten(segments))
                    .as("split of %s", input)
                    .isEqualTo(input.stream().filter(value -> value != 0).toList());
            assertThat(flatten(segments)).doesNotContain(0);
            // One segment per delimiter, plus a trailing one when the input does not end on one.
            long delimiters = input.stream().filter(value -> value == 0).count();
            boolean trailingContent = !input.isEmpty() && input.get(input.size() - 1) != 0;
            assertThat(segments).hasSize((int) delimiters + (trailingContent ? 1 : 0));
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {22L, 47L, 66_260L})
    void groupAdjacentByAgreesWithGroupAdjacentOnKeyEquality(long seed) {
        Random random = new Random(seed);
        for (int round = 0; round < ROUNDS; round++) {
            List<Integer> input = random.ints(random.nextInt(MAX_ELEMENTS), 0, 30).boxed().toList();

            List<List<Integer>> byKey =
                    StreamUtils.groupAdjacentBy(input.stream(), value -> value % 4).toList();
            List<List<Integer>> byRelation = StreamUtils
                    .groupAdjacent(input.stream(), (a, b) -> a % 4 == b % 4)
                    .toList();

            assertThat(byKey).isEqualTo(byRelation);
            assertThat(flatten(byKey)).isEqualTo(input);
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {4L, 11L, 31_415L})
    void mergeSortedMatchesConcatenateThenSort(long seed) {
        Random random = new Random(seed);
        for (int round = 0; round < ROUNDS; round++) {
            List<Integer> left = sorted(randomInput(random));
            List<Integer> right = sorted(randomInput(random));

            List<Integer> merged = StreamUtils
                    .mergeSorted(left.stream(), right.stream(), Comparator.<Integer>naturalOrder())
                    .toList();

            List<Integer> expected = Stream.concat(left.stream(), right.stream()).sorted().toList();
            assertThat(merged)
                    .as("merge of %d and %d sorted elements", left.size(), right.size())
                    .isEqualTo(expected);
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {5L, 13L, 16_180L})
    void interleaveKeepsEveryElementAndTheOrderWithinEachSide(long seed) {
        Random random = new Random(seed);
        for (int round = 0; round < ROUNDS; round++) {
            List<Integer> left = randomInput(random);
            List<Integer> right = randomInput(random);

            List<Pair<Integer, Integer>> tagged = StreamUtils.interleave(
                            left.stream().map(value -> Pair.of(0, value)),
                            right.stream().map(value -> Pair.of(1, value)))
                    .toList();

            assertThat(tagged).hasSize(left.size() + right.size());
            assertThat(sideOf(tagged, 0)).isEqualTo(left);
            assertThat(sideOf(tagged, 1)).isEqualTo(right);
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {6L, 17L, 27_182L})
    void scanEndsWhereReduceDoes(long seed) {
        Random random = new Random(seed);
        for (int round = 0; round < ROUNDS; round++) {
            List<Integer> input = randomInput(random);

            List<Integer> running = StreamUtils.scan(input.stream(), 0, Integer::sum).toList();

            assertThat(running).hasSize(input.size() + 1);
            assertThat(running.get(0)).isZero();
            assertThat(running.get(running.size() - 1))
                    .isEqualTo(input.stream().mapToInt(Integer::intValue).sum());
            // Each step differs from the previous one by exactly the element consumed.
            for (int i = 0; i < input.size(); i++) {
                assertThat(running.get(i + 1) - running.get(i)).isEqualTo(input.get(i));
            }
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {8L, 19L, 14_142L})
    void zipIsTheElementwisePairingOfBothInputs(long seed) {
        Random random = new Random(seed);
        for (int round = 0; round < ROUNDS; round++) {
            List<Integer> left = randomInput(random);
            List<Integer> right = randomInput(random);

            List<Pair<Integer, Integer>> zipped =
                    StreamUtils.zip(left.stream(), right.stream()).toList();

            int expected = Math.min(left.size(), right.size());
            assertThat(zipped).hasSize(expected);
            for (int i = 0; i < expected; i++) {
                assertThat(zipped.get(i)).isEqualTo(Pair.of(left.get(i), right.get(i)));
            }
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {10L, 23L, 57_721L})
    void takeUntilReturnsThePrefixEndingAtTheFirstMatch(long seed) {
        Random random = new Random(seed);
        for (int round = 0; round < ROUNDS; round++) {
            List<Integer> input = random.ints(random.nextInt(MAX_ELEMENTS), 0, 10).boxed().toList();
            int target = random.nextInt(10);

            List<Integer> taken =
                    StreamUtils.takeUntil(input.stream(), value -> value == target).toList();

            int firstMatch = input.indexOf(target);
            List<Integer> expected =
                    firstMatch < 0 ? input : input.subList(0, firstMatch + 1);
            assertThat(taken)
                    .as("takeUntil(== %d) over %s", target, input)
                    .isEqualTo(expected);
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {12L, 29L, 69_314L})
    void rangeMatchesIntStreamRangeSequentiallyAndInParallel(long seed) {
        Random random = new Random(seed);
        for (int round = 0; round < ROUNDS; round++) {
            int start = random.nextInt(2_000) - 1_000;
            int end = start + random.nextInt(2_000) - 500;

            List<Integer> expected = IntStream.range(start, Math.max(start, end)).boxed().toList();

            assertThat(StreamUtils.range(start, end)).isEqualTo(expected);
            assertThat(StreamUtils.range(start, end).parallel().toList())
                    .as("range(%d, %d) must survive splitting", start, end)
                    .isEqualTo(expected);
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {14L, 37L, 86_400L})
    void distinctByKeepsTheFirstOccurrenceOfEachKey(long seed) {
        Random random = new Random(seed);
        for (int round = 0; round < ROUNDS; round++) {
            List<Integer> input = random.ints(random.nextInt(MAX_ELEMENTS), 0, 8).boxed().toList();

            List<Integer> distinct = StreamUtils.distinctBy(input.stream(), key -> key % 3).toList();

            List<Integer> expected = new ArrayList<>();
            Set<Integer> seenKeys = new HashSet<>();
            for (Integer value : input) {
                if (seenKeys.add(value % 3)) {
                    expected.add(value);
                }
            }
            assertThat(distinct).isEqualTo(expected);
        }
    }

    private static List<Integer> randomInput(Random random) {
        int size = random.nextInt(MAX_ELEMENTS);
        return random.ints(size, -100, 100).boxed().toList();
    }

    private static List<Integer> sorted(List<Integer> values) {
        return values.stream().sorted().toList();
    }

    private static List<Integer> flatten(List<List<Integer>> groups) {
        return groups.stream().flatMap(List::stream).toList();
    }

    private static List<Integer> sideOf(List<Pair<Integer, Integer>> tagged, int side) {
        return tagged.stream()
                .filter(pair -> pair.first() == side)
                .map(Pair::second)
                .collect(Collectors.toList());
    }
}
