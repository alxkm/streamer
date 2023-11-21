# Streamer
### Java stream utility library, which someone can find useful 


All method are available in StreamUtils class like static methods

#### unique()

Returns a collector that accumulates elements into a list while ensuring uniqueness.

```java
public static <T> Collector<T, Set<T>, List<T>> unique() {
    // ... (method implementation)
}
```

#### addToSet()
Merges two sets by adding all elements from the second set to the first set.

```java
private static <T> Set<T> addToSet(Set<T> first, Set<T> second) {
    // ... (method implementation)
}
```

#### asStream(Iterable<T> iterable)
Converts an Iterable into a Stream.

```java
public static <T> Stream<T> asStream(Iterable<T> iterable) {
    // ... (method implementation)
}
```

#### arrayToCollection(Class<? extends Collection> collectionType, T[] array)
Converts an array to a collection of the specified type.

```java
public static <T> Collection<T> arrayToCollection(Class<? extends Collection> collectionType, T[] array) {
        // ... (method implementation)
}
```