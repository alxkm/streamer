package org.streamer;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoreCollectorsTest {

    @Test
    void uniqueDropsDuplicatesAndKeepsFirstAppearanceOrder() {
        List<Integer> result = Stream.of(3, 1, 3, 2, 1).collect(MoreCollectors.unique());

        assertThat(result).containsExactly(3, 1, 2);
    }

    @Test
    void uniqueOfAnEmptyStreamIsAnEmptyList() {
        assertThat(Stream.<String>empty().collect(MoreCollectors.unique())).isEmpty();
    }

    @Test
    void uniqueSurvivesAParallelStream() {
        List<Integer> result = IntStream.range(0, 10_000)
                .boxed()
                .parallel()
                .map(i -> i % 500)
                .collect(MoreCollectors.unique());

        assertThat(result).hasSize(500).doesNotHaveDuplicates();
    }

    @Test
    void uniqueWorksAsADownstreamCollector() {
        record Post(String author, String tag) { }
        List<Post> posts = List.of(
                new Post("ann", "java"), new Post("ann", "java"), new Post("ann", "streams"),
                new Post("bob", "gradle"));

        Map<String, List<String>> tagsPerAuthor = posts.stream()
                .collect(Collectors.groupingBy(Post::author,
                        Collectors.mapping(Post::tag, MoreCollectors.unique())));

        assertThat(tagsPerAuthor).containsExactlyInAnyOrderEntriesOf(Map.of(
                "ann", List.of("java", "streams"),
                "bob", List.of("gradle")));
    }

    @Test
    void toFrequencyMapCountsOccurrences() {
        Map<String, Long> counts =
                Stream.of("a", "b", "a", "c", "a").collect(MoreCollectors.toFrequencyMap());

        assertThat(counts).containsExactlyInAnyOrderEntriesOf(Map.of("a", 3L, "b", 1L, "c", 1L));
    }

    @Test
    void toFrequencyMapMergesPartialResultsInParallel() {
        Map<Integer, Long> counts = IntStream.range(0, 10_000)
                .boxed()
                .parallel()
                .map(i -> i % 4)
                .collect(MoreCollectors.toFrequencyMap());

        assertThat(counts).hasSize(4).allSatisfy((key, count) -> assertThat(count).isEqualTo(2_500L));
    }

    @Test
    void minMaxFindsBothBoundsInOnePass() {
        Optional<Pair<Integer, Integer>> bounds =
                Stream.of(3, 1, 4, 1, 5).collect(MoreCollectors.minMax(Comparator.naturalOrder()));

        assertThat(bounds).contains(Pair.of(1, 5));
    }

    @Test
    void minMaxOfAnEmptyStreamIsEmpty() {
        assertThat(Stream.<Integer>empty().collect(MoreCollectors.minMax(Comparator.naturalOrder())))
                .isEmpty();
    }

    @Test
    void minMaxOfASingleElementReturnsItTwice() {
        assertThat(Stream.of(7).collect(MoreCollectors.minMax(Comparator.<Integer>naturalOrder())))
                .contains(Pair.of(7, 7));
    }

    @Test
    void minMaxCombinesPartialResultsInParallel() {
        Optional<Pair<Integer, Integer>> bounds = IntStream.range(-5_000, 5_000)
                .boxed()
                .parallel()
                .collect(MoreCollectors.minMax(Comparator.naturalOrder()));

        assertThat(bounds).contains(Pair.of(-5_000, 4_999));
    }

    @Test
    void minMaxHonoursACustomComparator() {
        Optional<Pair<String, String>> bounds = Stream.of("bb", "a", "ccc")
                .collect(MoreCollectors.minMax(Comparator.comparingInt(String::length)));

        assertThat(bounds).contains(Pair.of("a", "ccc"));
    }

    @Test
    void minMaxRejectsANullComparator() {
        assertThatNullPointerException().isThrownBy(() -> MoreCollectors.minMax(null));
    }

    @Test
    void toLinkedMapPreservesEncounterOrder() {
        Map<String, Integer> map = Stream.of("ccc", "a", "bb")
                .collect(MoreCollectors.toLinkedMap(s -> s, String::length));

        assertThat(map).containsExactly(
                Map.entry("ccc", 3), Map.entry("a", 1), Map.entry("bb", 2));
    }

    @Test
    void toLinkedMapNamesTheDuplicateKey() {
        assertThatIllegalStateException()
                .isThrownBy(() -> Stream.of("a", "bb", "cc")
                        .collect(MoreCollectors.toLinkedMap(String::length, s -> s)))
                .withMessageContaining("Duplicate key 2")
                .withMessageContaining("bb")
                .withMessageContaining("cc");
    }

    @Test
    void toLinkedMapDetectsDuplicatesAcrossParallelPartitions() {
        assertThatThrownBy(() -> IntStream.range(0, 1_000)
                .boxed()
                .parallel()
                .collect(MoreCollectors.toLinkedMap(i -> i % 10, i -> i)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void toLinkedMapRejectsNullMappers() {
        assertThatNullPointerException().isThrownBy(() -> MoreCollectors.toLinkedMap(null, s -> s));
        assertThatNullPointerException().isThrownBy(() -> MoreCollectors.toLinkedMap(s -> s, null));
    }

    @Test
    void theClassCannotBeInstantiated() throws Exception {
        var constructor = MoreCollectors.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance).hasRootCauseInstanceOf(AssertionError.class);
    }
}
