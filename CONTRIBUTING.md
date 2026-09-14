# Contributing

Thanks for taking the time. This is a small library, so the bar is simple: every method has to earn
its place, and it has to be correct on the edges.

## Build and test

You need a JDK 17 or newer. Gradle downloads the right toolchain itself, so no local setup beyond
that is required.

```bash
./gradlew build          # compile, test, coverage gate, javadoc, jars
./gradlew test           # tests only
./gradlew jacocoTestReport
# open build/reports/jacoco/test/html/index.html
./gradlew jmh            # benchmarks, about six minutes
```

`build` fails if instruction coverage drops below 95% or branch coverage below 85%. Javadoc runs
with doclint enabled, so a broken `{@link}` or a missing `@param` breaks the build too.

## What belongs here

An operator belongs in this library when it is:

- **not already in the JDK.** `toList`, `groupingBy`, `takeWhile` and friends exist, and on JDK 24
  `Gatherers` covers fixed and sliding windows. A wrapper that saves three characters is noise, and
  earlier versions of this library carried a dozen of them.
- **lazy.** Operators that buffer the whole stream defeat the point. `batch` holds one batch,
  `windowed` holds one window, `mergeSorted` holds two elements.
- **honest about ordering.** If an operator only makes sense sequentially, say so in the Javadoc
  and make the implementation enforce it, the way `zipWithIndex` calls `sequential()`.

If you are unsure, open an issue with the signature and a call site before writing the code.

## Code style

- Four spaces, no tabs. Lines up to 110 characters.
- Public methods validate their arguments eagerly with `Objects.requireNonNull` and a parameter
  name. Validation belongs at the call, not inside a lambda the pipeline may never run.
- Numeric arguments that must be positive go through `requirePositive`, which names the parameter
  and the offending value in the message.
- Javadoc on every public method: a one-line summary, a paragraph on the behaviour that is not
  obvious from the signature, `@param` / `@return` / `@throws`, and a short `{@code}` example for
  anything non-trivial.
- Comments explain *why*, not *what*. The code already says what.

## Tests

Every operator needs at least:

- the happy path,
- the empty stream,
- the single element stream,
- the boundary case specific to the operator (a short tail for `batch`, a source shorter than the
  window for `windowed`, an unequal length for `zip`),
- the invalid arguments, both null and out of range.

Tests use JUnit 5 and AssertJ, grouped with `@Nested`. Method names are sentences:
`batchKeepsAShorterTail`, not `testBatch2`.

Beyond the per-case tests there are three other layers, and a new operator belongs in all of them:

- `OperatorPropertiesTest` drives the operator with randomly sized inputs from fixed seeds and
  checks its invariants. Seeds are fixed so a failure reproduces exactly; the failing seed is the
  name of the failing invocation. This is what caught the negative-range split bug.
- `SpliteratorContractTest` covers size estimates, splitting and repeated draining, the parts a
  "collect and compare" test never reaches.
- `ReadmeExamplesTest` runs the snippets printed in `README.md`, so an example cannot go stale.

Every operator also needs a fluent form on `Streamer` plus a line in `StreamerTest`. The chain must
keep returning `Streamer`, never a plain `Stream`.

## Benchmarks

`src/jmh` holds the JMH suite behind the performance table in the README. It is not part of `build`
and CI never runs it.

Two rules:

- **Measure before optimising, and measure again after.** The one "obvious" allocation fix that was
  tried here, hoisting a per-element `Consumer` out of `BatchSpliterator.tryAdvance` into a field,
  made that benchmark 1.75x slower. There is a comment in the code saying so; do not remove it and
  do not redo the change without numbers.
- **Compare against what someone would write by hand**, not against nothing. A benchmark showing
  that an operator takes some number of microseconds says nothing on its own.

If you change a number in the README table, say which machine and JDK produced it.

## Commits and pull requests

- One logical change per pull request.
- Commit subjects in the imperative mood, under 72 characters: `add groupAdjacent operator`, not
  `added some stuff`.
- Add an entry to `CHANGELOG.md` under `Unreleased`.
- English only, in code, comments, commits and issues.

## Releasing

Releases are cut from `master` by tagging. Before tagging, set `version` in `build.gradle` and move
the `Unreleased` section of `CHANGELOG.md` under the new version number in the same commit.

```bash
git tag -a v2.0.0 -m "2.0.0"
git push origin v2.0.0
```

The release workflow then builds the tag, refuses to continue if it does not match the project
version or has no CHANGELOG section, and publishes a GitHub release with the jars attached. JitPack
builds the same tag on first request, so nothing has to be uploaded by hand.
