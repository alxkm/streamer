package org.streamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collector;

public class StreamUtils {
    private static final Logger logger = LogManager.getLogger(StreamUtils.class);

    public static <T> Collector<T, Set<T>, List<T>> unique() {
        return Collector.of(HashSet::new,
                            Set::add,
                            StreamUtils::addToSet,
                            ArrayList::new,
                            Collector.Characteristics.CONCURRENT,
                            Collector.Characteristics.UNORDERED);
    }

    private static <T> Set<T> addToSet(Set<T> first, Set<T> second) {
        first.addAll(second);
        return first;
    }

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
