package org.streamer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collector;

public class StreamUtils {
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

}
