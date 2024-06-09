# Streamer
### Java stream utility library, which someone can find useful 

## Usage and StreamUtils class overview

## Overview
`StreamUtils` is a utility class providing various helpful methods for working with Java Streams. It includes methods for stream creation, transformation, collection, and more.

## Methods

### unique
```java
public static <T> Collector<T, Set<T>, List<T>> unique()
```
Returns a collector that accumulates elements into a list while ensuring uniqueness.

**Usage Example:**
```java
Stream<String> stream = Stream.of("apple", "banana", "apple");
List<String> uniqueList = stream.collect(StreamUtils.unique());
```

### asStream (Iterator)
```java
public static <T> Stream<T> asStream(Iterator<T> iterator)
```
Converts an `Iterator` into a `Stream`.

**Usage Example:**
```java
Iterator<String> iterator = List.of("a", "b", "c").iterator();
Stream<String> stream = StreamUtils.asStream(iterator);
```

### asStream (Iterable)
```java
public static <T> Stream<T> asStream(Iterable<T> iterable)
```
Converts an `Iterable` into a `Stream`.

**Usage Example:**
```java
Iterable<String> iterable = List.of("a", "b", "c");
Stream<String> stream = StreamUtils.asStream(iterable);
```

### arrayToCollection
```java
public static <T> Collection<T> arrayToCollection(Class<? extends Collection> collectionType, T[] array)
```
Converts an array to a collection of the specified type.

**Parameters:**
- `collectionType`: The class representing the desired collection type.
- `array`: The array to be converted to a collection.

**Usage Example:**
```java
String[] array = {"a", "b", "c"};
List<String> list = (List<String>) StreamUtils.arrayToCollection(ArrayList.class, array);
```

### filterByType
```java
public static <T> Stream<T> filterByType(Stream<?> stream, Class<T> clazz)
```
Filters elements of a stream by a specific type and casts them to that type.

**Parameters:**
- `stream`: The original stream.
- `clazz`: The class to filter by.

**Usage Example:**
```java
Stream<Object> stream = Stream.of(1, "a", 2, "b", 3);
Stream<String> stringStream = StreamUtils.filterByType(stream, String.class);
```

### toList
```java
public static <T> List<T> toList(Stream<T> stream)
```
Collects elements of a stream into a list.

**Usage Example:**
```java
Stream<String> stream = Stream.of("a", "b", "c");
List<String> list = StreamUtils.toList(stream);
```

### toSet
```java
public static <T> Set<T> toSet(Stream<T> stream)
```
Collects elements of a stream into a set.

**Usage Example:**
```java
Stream<String> stream = Stream.of("a", "b", "c", "a");
Set<String> set = StreamUtils.toSet(stream);
```

### toMap
```java
public static <T, K, U> Map<K, U> toMap(Stream<T> stream, Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends U> valueMapper)
```
Collects elements of a stream into a map.

**Parameters:**
- `stream`: The original stream.
- `keyMapper`: A function to generate keys.
- `valueMapper`: A function to generate values.

**Usage Example:**
```java
Stream<String> stream = Stream.of("a", "bb", "ccc");
Map<Integer, String> map = StreamUtils.toMap(stream, String::length, Function.identity());
```

### groupBy
```java
public static <T, K> Map<K, List<T>> groupBy(Stream<T> stream, Function<? super T, ? extends K> classifier)
```
Groups elements of a stream by a classifier function.

**Parameters:**
- `stream`: The original stream.
- `classifier`: A function to classify elements.

**Usage Example:**
```java
Stream<String> stream = Stream.of("apple", "banana", "apricot", "cherry");
Map<Character, List<String>> grouped = StreamUtils.groupBy(stream, s -> s.charAt(0));
```

### partitionBy
```java
public static <T> Map<Boolean, List<T>> partitionBy(Stream<T> stream, Predicate<? super T> predicate)
```
Partitions elements of a stream by a predicate.

**Parameters:**
- `stream`: The original stream.
- `predicate`: A predicate to partition elements.

**Usage Example:**
```java
Stream<Integer> stream = Stream.of(1, 2, 3, 4, 5);
Map<Boolean, List<Integer>> partitioned = StreamUtils.partitionBy(stream, x -> x % 2 == 0);
```

### streamOfNullable
```java
public static <T> Stream<T> streamOfNullable(T element)
```
Returns a stream containing a single element if the element is non-null, otherwise returns an empty stream.

**Parameters:**
- `element`: The element.

**Usage Example:**
```java
Stream<String> stream = StreamUtils.streamOfNullable("a");
```

### concatStreams
```java
@SafeVarargs
public static <T> Stream<T> concatStreams(Stream<T>... streams)
```
Concatenates multiple streams into one stream.

**Usage Example:**
```java
Stream<String> stream1 = Stream.of("a", "b");
Stream<String> stream2 = Stream.of("c", "d");
Stream<String> result = StreamUtils.concatStreams(stream1, stream2);
```

### zip
```java
public static <A, B> Stream<Pair<A, B>> zip(Stream<A> a, Stream<B> b)
```
Zips two streams into a single stream of pairs.

**Parameters:**
- `a`: The first stream.
- `b`: The second stream.

**Usage Example:**
```java
Stream<String> streamA = Stream.of("a", "b", "c");
Stream<Integer> streamB = Stream.of(1, 2, 3);
Stream<Pair<String, Integer>> zipped = StreamUtils.zip(streamA, streamB);
```

### peekAndReturn
```java
public static <T> Stream<T> peekAndReturn(Stream<T> stream, Consumer<? super T> action)
```
Performs an action on each element of a stream and returns the stream.

**Parameters:**
- `stream`: The original stream.
- `action`: The action to perform.

**Usage Example:**
```java
Stream<String> stream = Stream.of("a", "b", "c");
Stream<String> result = StreamUtils.peekAndReturn(stream, System.out::println);
```

### findFirst
```java
public static <T> Optional<T> findFirst(Stream<T> stream)
```
Finds the first element of a stream, if present.

**Usage Example:**
```java
Stream<String> stream = Stream.of("a", "b", "c");
Optional<String> first = StreamUtils.findFirst(stream);
```

### batchProcess
```java
public static <T> Stream<List<T>> batchProcess(Stream<T> stream, int batchSize)
```
Batches elements of a stream into lists of a given size.

**Parameters:**
- `stream`: The original stream.
- `batchSize`: The size of the batches.

**Usage Example:**
```java
Stream<Integer> stream = Stream.of(1, 2, 3, 4, 5);
Stream<List<Integer>> batches = StreamUtils.batchProcess(stream, 2);
```

### parallelFilter
```java
public static <T> Stream<T> parallelFilter(Stream<T> stream, Predicate<? super T> predicate)
```
Filters elements of a stream in parallel based on a predicate.

**Parameters:**
- `stream`: The original stream.
- `predicate`: The predicate to filter elements.

**Usage Example:**
```java
Stream<Integer> stream = Stream.of(1, 2, 3, 4, 5);
List<Integer> evenNumbers = StreamUtils.parallelFilter(stream, x -> x % 2 == 0).collect(Collectors.toList());
```

### parallelMap
```java
public static <T, R> Stream<R> parallelMap(Stream<T> stream, Function<? super T, ? extends R> mapper)
```
Maps elements of a stream in parallel using a mapper function.

**Parameters:**
- `stream`: The original stream.
- `mapper`: The mapper function to apply to elements.

**Usage Example:**
```java
Stream<Integer> stream = Stream.of(1, 2, 3, 4, 5);
List<Integer> doubled = StreamUtils.parallelMap(stream, x -> x * 2).collect(Collectors.toList());
```

### distinctByKey
```java
public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor)
```
Creates a predicate that maintains state to allow only distinct elements based on a key extractor function.

**Parameters:**
- `keyExtractor`: The function to extract keys.

**Usage Example:**
```java
Stream<String> stream = Stream.of("apple", "banana", "apricot", "cherry");
List<String> distinct = stream.filter(StreamUtils.distinctByKey(s -> s.charAt(0))).collect(Collectors.toList());
```

### streamify (Iterator)
```java
public static <T> Stream<T> streamify(Iterator<T> iterator)
```
Creates a stream from an iterator.

**Usage Example:**
```java


Iterator<String> iterator = List.of("a", "b", "c").iterator();
Stream<String> stream = StreamUtils.streamify(iterator);
```

### streamify (Iterable)
```java
public static <T> Stream<T> streamify(Iterable<T> iterable)
```
Creates a stream from an iterable.

**Usage Example:**
```java
Iterable<String> iterable = List.of("a", "b", "c");
Stream<String> stream = StreamUtils.streamify(iterable);
```

### flatMapToPair
```java
public static <T, U> Stream<Pair<T, U>> flatMapToPair(Stream<T> stream, Function<? super T, Stream<U>> mapper)
```
Flattens a stream of collections to a stream of pairs.

**Parameters:**
- `stream`: The original stream.
- `mapper`: The function to generate a stream from each element.

**Usage Example:**
```java
Stream<String> stream = Stream.of("a", "b");
Stream<Pair<String, Integer>> pairs = StreamUtils.flatMapToPair(stream, s -> Stream.of(s.length()));
```

### filterNot
```java
public static <T> Stream<T> filterNot(Stream<T> stream, Predicate<? super T> predicate)
```
Filters elements that do not match the given predicate.

**Parameters:**
- `stream`: The original stream.
- `predicate`: The predicate to filter elements.

**Usage Example:**
```java
Stream<Integer> stream = Stream.of(1, 2, 3, 4, 5);
Stream<Integer> oddNumbers = StreamUtils.filterNot(stream, x -> x % 2 == 0);
```

### takeWhile
```java
public static <T> Stream<T> takeWhile(Stream<T> stream, Predicate<? super T> predicate)
```
Takes elements while the predicate is true.

**Parameters:**
- `stream`: The original stream.
- `predicate`: The predicate to test elements.

**Usage Example:**
```java
Stream<Integer> stream = Stream.of(1, 2, 3, 4, 5);
Stream<Integer> taken = StreamUtils.takeWhile(stream, x -> x < 4);
```

### mapToIndex
```java
public static <T> Stream<Pair<Integer, T>> mapToIndex(Stream<T> stream)
```
Maps elements to their index positions.

**Parameters:**
- `stream`: The original stream.

**Usage Example:**
```java
Stream<String> stream = Stream.of("a", "b", "c");
Stream<Pair<Integer, String>> indexed = StreamUtils.mapToIndex(stream);
```

## License
This code is licensed under the MIT License.