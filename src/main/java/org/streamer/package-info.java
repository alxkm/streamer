/**
 * Stream operators and collectors that fill the gaps in {@code java.util.stream}.
 *
 * <p>Four types make up the public API:</p>
 * <ul>
 *   <li>{@link org.streamer.Streamer} - a {@link java.util.stream.Stream} that carries every
 *       operator as an instance method, so chains read in the order the data flows</li>
 *   <li>{@link org.streamer.StreamUtils} - the same operators as static methods: stream creation,
 *       filtering, reshaping and merging</li>
 *   <li>{@link org.streamer.MoreCollectors} - collectors that complement
 *       {@link java.util.stream.Collectors}</li>
 *   <li>{@link org.streamer.Pair} - the immutable tuple returned by the pairwise operators</li>
 * </ul>
 *
 * <p>Everything else in this package is package private: the spliterators that back the
 * operators are implementation detail and carry no compatibility promise.</p>
 *
 * <p>The library has no runtime dependencies and holds no mutable global state. It is a JPMS
 * module named {@code org.streamer} that exports this package and nothing else.</p>
 */
package org.streamer;
