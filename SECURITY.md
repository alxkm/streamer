# Security policy

## Supported versions

| Version | Supported |
| --- | --- |
| 2.0.x | yes |
| 1.x | no |

## Reporting a vulnerability

Report privately through
[GitHub security advisories](https://github.com/alxkm/streamer/security/advisories/new) rather than
by opening a public issue.

Please include the affected version, a reproducer, and what an attacker gains. Expect a first reply
within a week.

## Scope

The library has no runtime dependencies, performs no I/O and opens no network connections, so the
realistic surface is small. The parts worth a second look:

- `arrayToCollection(Class, array)` instantiates a class reflectively. Passing a caller-controlled
  class name to it means the caller chooses which no-argument constructor runs. Prefer
  `arrayToCollection(Supplier, array)`, which cannot be steered that way.
- Operators that buffer (`batch`, `windowed`, `groupAdjacent`) allocate proportionally to the size
  argument or the run length. Deriving those from untrusted input is a memory exhaustion risk in
  your code, not in the library.
