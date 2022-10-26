package org.streamer;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class StreamUtilsTest {
    @Test
    public void uniqueCollectorTest() {
        final List<Integer> actual = Stream.of(1, 2, 3, 4, 3, 4, 5).collect(StreamUtils.unique());
        final List<Integer> expected = List.of(1, 2, 3, 4, 5);

        assertEquals(actual.size(), expected.size());
    }
}
