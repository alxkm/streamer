package org.streamer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    @Test
    void testFilterByType() {
        Stream<Object> mixedStream = Stream.of(1, "two", 3, "four");
        Stream<String> stringStream = StreamUtils.filterByType(mixedStream, String.class);
        assertArrayEquals(new String[]{"two", "four"}, stringStream.toArray());
    }

    @Test
    void testToList() {
        Stream<Integer> stream = Stream.of(1, 2, 3);
        List<Integer> list = StreamUtils.toList(stream);
        assertEquals(Arrays.asList(1, 2, 3), list);
    }

    @Test
    void testToSet() {
        Stream<Integer> stream = Stream.of(1, 2, 3, 2);
        Set<Integer> set = StreamUtils.toSet(stream);
        assertEquals(new HashSet<>(Arrays.asList(1, 2, 3)), set);
    }

    @Test
    void testToMap() {
        Stream<String> stream = Stream.of("a", "bb", "ccc");
        Map<Integer, String> map = StreamUtils.toMap(stream, String::length, Function.identity());
        Map<Integer, String> expected = new HashMap<>();
        expected.put(1, "a");
        expected.put(2, "bb");
        expected.put(3, "ccc");
        assertEquals(expected, map);
    }

    @Test
    void testGroupBy() {
        Stream<String> stream = Stream.of("a", "bb", "ccc", "d");
        Map<Integer, List<String>> map = StreamUtils.groupBy(stream, String::length);
        Map<Integer, List<String>> expected = new HashMap<>();
        expected.put(1, Arrays.asList("a", "d"));
        expected.put(2, Collections.singletonList("bb"));
        expected.put(3, Collections.singletonList("ccc"));
        assertEquals(expected, map);
    }

    @Test
    void testPartitionBy() {
        Stream<Integer> stream = Stream.of(1, 2, 3, 4, 5);
        Map<Boolean, List<Integer>> map = StreamUtils.partitionBy(stream, x -> x % 2 == 0);
        assertEquals(Arrays.asList(2, 4), map.get(true));
        assertEquals(Arrays.asList(1, 3, 5), map.get(false));
    }

    @Test
    void testStreamOfNullable() {
        Stream<String> stream = StreamUtils.streamOfNullable("test");
        assertArrayEquals(new String[]{"test"}, stream.toArray());

        Stream<String> nullStream = StreamUtils.streamOfNullable(null);
        assertEquals(0, nullStream.count());
    }

    @Test
    void testConcatStreams() {
        Stream<String> stream1 = Stream.of("a", "b");
        Stream<String> stream2 = Stream.of("c", "d");
        Stream<String> resultStream = StreamUtils.concatStreams(stream1, stream2);
        assertArrayEquals(new String[]{"a", "b", "c", "d"}, resultStream.toArray());
    }

    @Test
    void testZip() {
        Stream<String> stream1 = Stream.of("a", "b");
        Stream<Integer> stream2 = Stream.of(1, 2);
        List<Pair<String, Integer>> zipped = StreamUtils.zip(stream1, stream2).toList();
        assertEquals(Arrays.asList(new Pair<>("a", 1), new Pair<>("b", 2)), zipped);
    }

    @Test
    void testPeekAndReturn() {
        List<Integer> list = new ArrayList<>();
        Stream<Integer> stream = Stream.of(1, 2, 3);
        StreamUtils.peekAndReturn(stream, list::add).forEach(x -> {
        });
        assertEquals(Arrays.asList(1, 2, 3), list);
    }

    @Test
    void testFindFirst() {
        Stream<Integer> stream = Stream.of(1, 2, 3);
        assertEquals(Optional.of(1), StreamUtils.findFirst(stream));
    }

    @Test
    void testBatchProcess() {
        Stream<Integer> stream = Stream.of(1, 2, 3, 4, 5, 6);
        List<List<Integer>> batches = StreamUtils.batchProcess(stream, 2).toList();
        assertEquals(Arrays.asList(Arrays.asList(1, 2), Arrays.asList(3, 4), Arrays.asList(5, 6)), batches);
    }

    @Test
    void testParallelFilter() {
        Stream<Integer> stream = Stream.of(1, 2, 3, 4, 5);
        List<Integer> evenNumbers = StreamUtils.parallelFilter(stream, x -> x % 2 == 0).toList();
        assertEquals(Arrays.asList(2, 4), evenNumbers);
    }

    @Test
    void testParallelMap() {
        Stream<Integer> stream = Stream.of(1, 2, 3, 4, 5);
        List<Integer> doubled = StreamUtils.parallelMap(stream, x -> x * 2).toList();
        assertEquals(Arrays.asList(2, 4, 6, 8, 10), doubled);
    }

    @Test
    void testDistinctByKey() {
        Stream<String> stream = Stream.of("apple", "banana", "apricot", "cherry");
        List<String> distinct = stream.filter(StreamUtils.distinctByKey(s -> s.charAt(0))).toList();
        assertEquals(Arrays.asList("apple", "banana", "cherry"), distinct);
    }

    @Test
    void testStreamifyIterator() {
        List<String> list = Arrays.asList("a", "b", "c");
        Iterator<String> iterator = list.iterator();
        List<String> result = StreamUtils.streamify(iterator).toList();
        assertEquals(list, result);
    }

    @Test
    void testStreamifyIterable() {
        List<String> list = Arrays.asList("a", "b", "c");
        List<String> result = StreamUtils.streamify(list).toList();
        assertEquals(list, result);
    }

    @Test
    void testFlatMapToPair() {
        Stream<String> stream = Stream.of("a", "b");
        Stream<Pair<String, Integer>> pairStream = StreamUtils.flatMapToPair(stream, s -> Stream.of(s.length(), s.length() + 1));
        List<Pair<String, Integer>> expected = Arrays.asList(
                new Pair<>("a", 1), new Pair<>("a", 2),
                new Pair<>("b", 1), new Pair<>("b", 2)
        );
        assertEquals(expected, pairStream.toList());
    }

    @Test
    void testFilterNot() {
        Stream<Integer> stream = Stream.of(1, 2, 3, 4, 5);
        Stream<Integer> result = StreamUtils.filterNot(stream, x -> x % 2 == 0);
        assertArrayEquals(new Integer[]{1, 3, 5}, result.toArray());
    }

    @Test
    void testTakeWhile() {
        Stream<Integer> stream = Stream.of(1, 2, 3, 4, 5);
        Stream<Integer> result = StreamUtils.takeWhile(stream, x -> x < 4);
        assertArrayEquals(new Integer[]{1, 2, 3}, result.toArray());
    }

    @Test
    void testMapToIndex() {
        Stream<String> stream = Stream.of("a", "b", "c");
        Stream<Pair<Integer, String>> result = StreamUtils.mapToIndex(stream);
        List<Pair<Integer, String>> expected = Arrays.asList(
                new Pair<>(0, "a"), new Pair<>(1, "b"), new Pair<>(2, "c")
        );
        assertEquals(expected, result.toList());
    }
}
