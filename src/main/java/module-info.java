/**
 * Stream operators and collectors that fill the gaps in {@code java.util.stream}.
 *
 * <p>The module has exactly one package and exports it. The spliterators behind the operators are
 * package private classes in that same package, so they are unreachable from outside the jar on
 * the class path as well as the module path.</p>
 */
module org.streamer {
    requires java.base;

    exports org.streamer;
}
