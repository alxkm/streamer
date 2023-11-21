package org.streamer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    public void testIteratorAsStream() {
        List<String> list = Arrays.asList("apple", "banana", "orange");
        Iterator<String> iterator = list.iterator();
        Stream<String> streamFromIterator = StreamUtils.asStream(iterator);
        assertNotNull(streamFromIterator);
        List<String> collectedList = streamFromIterator.collect(Collectors.toList());
        assertEquals(list.size(), collectedList.size());
        assertTrue(collectedList.containsAll(list));
    }

    @Test
    public void testEmptyIteratorAsStream() {
        List<String> list = Collections.emptyList();
        Iterator<String> emptyIterator = list.iterator();
        Stream<String> streamFromEmptyIterator = StreamUtils.asStream(emptyIterator);
        assertNotNull(streamFromEmptyIterator);
        List<String> collectedList = streamFromEmptyIterator.collect(Collectors.toList());
        assertTrue(collectedList.isEmpty());
    }

    @Test
    public void testIterableAsStream() {
        List<String> list = Arrays.asList("apple", "banana", "orange");
        Stream<String> streamFromIterable = StreamUtils.asStream(list);
        assertNotNull(streamFromIterable);
        List<String> collectedList = streamFromIterable.collect(Collectors.toList());
        assertEquals(list.size(), collectedList.size());
        assertTrue(collectedList.containsAll(list));
    }

    @Test
    public void testEmptyIterableAsStream() {
        Iterable<String> emptyIterable = Collections.emptyList();
        Stream<String> streamFromEmptyIterable = StreamUtils.asStream(emptyIterable);
        assertNotNull(streamFromEmptyIterable);
        List<String> collectedList = streamFromEmptyIterable.collect(Collectors.toList());
        assertTrue(collectedList.isEmpty());
    }
}
