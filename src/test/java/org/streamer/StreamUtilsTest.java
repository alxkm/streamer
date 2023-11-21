package org.streamer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
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

    @Test
    public void testArrayToArrayList() {
        String[] stringArray = {"apple", "banana", "orange"};
        Collection<String> convertedList = StreamUtils.arrayToCollection(ArrayList.class, stringArray);
        assertEquals(Arrays.asList(stringArray), new ArrayList<>(convertedList),
                "ArrayList conversion failed");
    }

    @Test
    public void testArrayToHashSet() {
        Integer[] intArray = {1, 2, 3, 4, 5};
        Collection<Integer> convertedSet = StreamUtils.arrayToCollection(HashSet.class, intArray);
        assertEquals(new HashSet<>(Arrays.asList(intArray)), new HashSet<>(convertedSet),
                "HashSet conversion failed");
    }
}
