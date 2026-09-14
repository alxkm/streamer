# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.0.0]

The library was reduced to the operators the JDK does not have, and the ones that remained were
audited for correctness. This release is not source compatible with 1.x; the migration table at the
bottom maps every removed method to its replacement.

### Added

- `Streamer`, a fluent front end that implements `Stream` and carries every operator as an instance
  method, so chains read in the order the data flows instead of inside out. It holds no state of
  its own and delegates to the wrapped stream, so laziness, ordering, parallelism and `close`
  behave exactly as with the static form. `pipe` applies any stream transform without leaving the
  chain, and `unwrap` returns the delegate.
- `module-info.java`. The jar is a JPMS module named `org.streamer` and exports one package.
- A JMH benchmark suite in `src/jmh`, run with `./gradlew jmh` and never in CI. Every performance
  claim in the README is a number it produced, including the places where a hand written loop wins.
- A release workflow: pushing a `vX.Y.Z` tag builds, checks the tag against the project version,
  extracts the matching CHANGELOG section and publishes a GitHub release with the jars.
- A workflow that publishes the Javadoc to GitHub Pages on every push to master.
- `MoreCollectors`, a new home for collectors: `unique()`, `toFrequencyMap()`, `minMax(Comparator)`
  and `toLinkedMap(keyMapper, valueMapper)`.
- `windowed(stream, size)` and `windowed(stream, size, step)` for sliding windows.
- `groupAdjacentBy(stream, keyExtractor)`, the keyed form of `groupAdjacent`. Each element's key is
  computed once.
- `splitBy(stream, delimiter)`, which cuts a stream into segments at delimiter elements and drops
  the delimiters. The lazy counterpart of `String.split` for streams.
- `scan(stream, seed, accumulator)` for running folds, the streaming counterpart of `reduce`.
- `groupAdjacent(stream, sameGroup)` for run-length grouping that keeps encounter order.
- `interleave(left, right)` and `mergeSorted(left, right, comparator)`.
- `takeUntil(stream, predicate)`, the inclusive complement of `Stream.takeWhile`.
- `distinctBy(stream, keyExtractor)` as a direct form of `filter(distinctByKey(..))`.
- `zip(left, right, combiner)`, which avoids allocating a `Pair` when the pair is consumed at once.
- `asStream(Enumeration)` for pre-collections APIs.
- `arrayToCollection(Supplier, array)`, a type-safe and reflection-free alternative to the
  reflective overload.
- `Pair` gained `of`, `fromEntry`, `toEntry`, `swap`, `mapFirst`, `mapSecond`, `fold` and
  `comparing`.

### Changed

- **`Pair` is now a public top-level record.** It used to be a package-private class declared in
  `StreamUtils.java` while appearing in the signatures of public methods, which made `zip`,
  `mapToIndex` and `flatMapToPair` unusable from outside `org.streamer`. Field access
  `pair.first` becomes the accessor `pair.first()`.
- **The library has no runtime dependencies.** `log4j-api` and `log4j-core` were pulled in for a
  single `logger.error` call in `arrayToCollection`; the failure is now reported through the
  exception cause instead.
- **Java baseline is 17**, matching what CI has been building against. The README previously
  claimed Java 8 while the code required 9 and the tests required 16.
- Every public method validates its arguments and names the offending parameter.
- The spliterators are package private classes in `org.streamer` and report accurate size
  estimates, so downstream pipelines can size their buffers. Nothing outside the jar can name
  them, on the class path or the module path.
- `unique()` moved to `MoreCollectors` and now preserves the order in which elements first appear.
- `concatStreams` is now `concat`, `batchProcess` is now `batch`, `mapToIndex` is now
  `zipWithIndex`, `streamOfNullable` is now `ofNullable`, and `customRangeAsStream` is now `range`.

### Fixed

- `unique()` declared `Collector.Characteristics.CONCURRENT` while accumulating into a plain
  `HashSet`. On a parallel stream the JDK shares one accumulator across threads for a concurrent
  collector, so the result was a data race that could lose elements or corrupt the set.
- `batchProcess(stream, 0)` produced an infinite stream of empty lists. `batch` rejects any size
  below one.
- `zip`, `batch` and the other spliterator-backed operators dropped the source stream's close
  handler, leaking the underlying resource when the source was a file or a database cursor. Each
  derived stream now closes its source.
- `mapToIndex` used an `AtomicInteger`, which produced non-deterministic indices on a parallel
  source. `zipWithIndex` forces the pipeline sequential.
- `arrayToCollection` swallowed the underlying reflection failure into a log line; the cause is now
  attached to the thrown `IllegalArgumentException`.
- An inverted range such as `customRangeAsStream(5, 1)` made the spliterator report a negative
  `estimateSize()`, which violates the `Spliterator` contract. `range` clamps the bound instead.
- `RangeSpliterator.trySplit` computed its midpoint as `(current + end) >>> 1`. The unsigned shift
  trick only holds for non-negative bounds: on a negative range the sum is negative and the shift
  turns it into a huge positive number, so `customRangeAsStream(-1000, -500).parallel()` tried to
  cover roughly two billion values that were never in the range. The midpoint is now computed in
  `long` arithmetic with an arithmetic shift.
- `groupAdjacent` allocated a scratch list per source element rather than per group.
- `zipWithIndex` counted in an `int` and wrapped silently to a negative index past 2^31 elements.
  It now throws `ArithmeticException` at that point.
- `mergeSorted` used `null` as its end-of-input marker, which made null elements illegal. It tracks
  exhaustion with flags, so nulls are fine whenever the comparator accepts them.
- `unique()` and `toFrequencyMap()` exposed their accumulator type (`Set<T>`, `Map<T, Long>`) in the
  public signature, pinning the implementation. Both return `Collector<T, ?, R>` now.

### Internal

- Each operator now has its own named spliterator. `takeUntil`, `scan` and `groupAdjacent` used to
  build anonymous inner classes inside `StreamUtils`, which left the same concept expressed two
  different ways depending on which operator you read.

### Removed

The following were one-to-one aliases for JDK calls and carried no behaviour of their own.

| Removed | Use instead |
| --- | --- |
| `toList(stream)` | `stream.toList()` |
| `toSet(stream)` | `stream.collect(Collectors.toSet())` |
| `toMap(stream, k, v)` | `stream.collect(Collectors.toMap(k, v))` |
| `groupBy(stream, f)` | `stream.collect(Collectors.groupingBy(f))` |
| `partitionBy(stream, p)` | `stream.collect(Collectors.partitioningBy(p))` |
| `findFirst(stream)` | `stream.findFirst()` |
| `peekAndReturn(stream, a)` | `stream.peek(a)` |
| `parallelFilter(stream, p)` | `stream.parallel().filter(p)` |
| `parallelMap(stream, f)` | `stream.parallel().map(f)` |
| `takeWhile(stream, p)` | `stream.takeWhile(p)` (JDK 9+) |
| `streamify(iterator)` | `StreamUtils.asStream(iterator)` |
| `streamify(iterable)` | `StreamUtils.asStream(iterable)` |

### Migration from 1.x

| 1.x | 2.0 |
| --- | --- |
| `StreamUtils.concatStreams(a, b)` | `StreamUtils.concat(a, b)` |
| `StreamUtils.batchProcess(s, n)` | `StreamUtils.batch(s, n)` |
| `StreamUtils.mapToIndex(s)` | `StreamUtils.zipWithIndex(s)` |
| `StreamUtils.streamOfNullable(x)` | `StreamUtils.ofNullable(x)` |
| `StreamUtils.customRangeAsStream(a, b)` | `StreamUtils.range(a, b)` |
| `stream.collect(StreamUtils.unique())` | `stream.collect(MoreCollectors.unique())` |
| nested `StreamUtils` calls | `Streamer.of(stream).windowed(3).batch(2)` |
| `pair.first`, `pair.second` | `pair.first()`, `pair.second()` |
| `StreamUtils.arrayToCollection(ArrayList.class, a)` | `StreamUtils.arrayToCollection(ArrayList::new, a)` |

## 1.0-SNAPSHOT

Everything before 2.0.0. Never tagged or published: stream creation helpers, collector wrappers
around `java.util.stream.Collectors`, `zip`, `batchProcess`, `distinctByKey` and a custom range
spliterator.

[Unreleased]: https://github.com/alxkm/streamer/compare/v2.0.0...HEAD
[2.0.0]: https://github.com/alxkm/streamer/releases/tag/v2.0.0
