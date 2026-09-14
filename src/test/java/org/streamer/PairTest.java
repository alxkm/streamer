package org.streamer;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PairTest {

    @Test
    void ofExposesBothComponents() {
        Pair<String, Integer> pair = Pair.of("a", 1);

        assertThat(pair.first()).isEqualTo("a");
        assertThat(pair.second()).isEqualTo(1);
    }

    @Test
    void pairsWithEqualComponentsAreEqual() {
        assertThat(Pair.of("a", 1))
                .isEqualTo(Pair.of("a", 1))
                .hasSameHashCodeAs(Pair.of("a", 1))
                .isNotEqualTo(Pair.of("a", 2));
    }

    @Test
    void nullComponentsAreAllowed() {
        Pair<String, String> pair = Pair.of(null, null);

        assertThat(pair.first()).isNull();
        assertThat(pair).isEqualTo(Pair.of(null, null));
    }

    @Test
    void toStringNamesBothComponents() {
        assertThat(Pair.of("a", 1)).hasToString("Pair[first=a, second=1]");
    }

    @Test
    void swapExchangesTheComponents() {
        assertThat(Pair.of("a", 1).swap()).isEqualTo(Pair.of(1, "a"));
    }

    @Test
    void mapFirstLeavesTheSecondComponentAlone() {
        assertThat(Pair.of("a", 1).mapFirst(String::toUpperCase)).isEqualTo(Pair.of("A", 1));
    }

    @Test
    void mapSecondLeavesTheFirstComponentAlone() {
        assertThat(Pair.of("a", 1).mapSecond(i -> i * 2)).isEqualTo(Pair.of("a", 2));
    }

    @Test
    void foldCollapsesBothComponents() {
        String folded = Pair.of("a", 1).fold((text, count) -> text.repeat(count + 1));

        assertThat(folded).isEqualTo("aa");
    }

    @Test
    void convertsToAndFromMapEntries() {
        Map<String, Integer> map = Map.of("a", 1);
        Map.Entry<String, Integer> entry = map.entrySet().iterator().next();

        assertThat(Pair.fromEntry(entry)).isEqualTo(Pair.of("a", 1));
        assertThat(Pair.of("a", 1).toEntry()).isEqualTo(entry);
    }

    @Test
    void entryConversionToleratesNulls() {
        assertThat(Pair.of(null, null).toEntry().getKey()).isNull();
    }

    @Test
    void comparingOrdersByFirstThenSecond() {
        List<Pair<String, Integer>> pairs = List.of(
                Pair.of("b", 1), Pair.of("a", 2), Pair.of("a", 1));

        Comparator<Pair<String, Integer>> byBoth =
                Pair.comparing(Comparator.naturalOrder(), Comparator.naturalOrder());

        assertThat(pairs.stream().sorted(byBoth))
                .containsExactly(Pair.of("a", 1), Pair.of("a", 2), Pair.of("b", 1));
    }
}
