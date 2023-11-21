package org.streamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class StreamUtils {
    private static final Logger logger = LogManager.getLogger(StreamUtils.class);

    /**
     * Returns a collector that accumulates elements into a list while ensuring uniqueness.
     * @param <T> the type of the input elements
     * @return a collector that accumulates elements into a list while ensuring uniqueness
     */
    public static <T> Collector<T, Set<T>, List<T>> unique() {
        return Collector.of(HashSet::new,
                            Set::add,
                            StreamUtils::addToSet,
                            ArrayList::new,
                            Collector.Characteristics.CONCURRENT,
                            Collector.Characteristics.UNORDERED);
    }

    /**
     * Merges two sets by adding all elements from the second set to the first set.
     * @param <T> the type of elements in the sets
     * @param first the first set
     * @param second the second set to be merged into the first set
     * @return the merged set containing elements from both input sets
     */
    private static <T> Set<T> addToSet(Set<T> first, Set<T> second) {
        first.addAll(second);
        return first;
    }

    /**
     * Converts an Iterator into a Stream.
     * @param <T> the type of elements in the Iterator and Stream
     * @param iterator the Iterator to be converted
     * @return a Stream containing elements from the Iterator
     */
    public static <T> Stream<T> asStream(Iterator<T> iterator) {
        Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.IMMUTABLE);
        return StreamSupport.stream(spliterator, false);
    }

    /**
     * Converts an Iterable into a Stream.
     * @param <T> the type of elements in the Iterable and Stream
     * @param iterable the Iterable to be converted
     * @return a Stream containing elements from the Iterable
     */
    public static <T> Stream<T> asStream(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Converts an array to a collection of the specified type.
     * @param <T> the type of elements in the array and collection
     * @param collectionType the class representing the desired collection type
     * @param array the array to be converted to a collection
     * @return a collection containing elements from the array
     * @throws IllegalArgumentException if the collection creation fails
     */
    public static <T> Collection<T> arrayToCollection(Class<? extends Collection> collectionType, T[] array) {
        try {
            Collection collection = collectionType.getDeclaredConstructor().newInstance();
            collection.addAll(Arrays.asList(array));
            return collection;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            logger.error("Error during collections creation", e);
            throw new IllegalArgumentException("Failed to create collection of specified type");
        }
    }
}
