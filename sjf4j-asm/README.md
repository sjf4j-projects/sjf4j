# sjf4j-asm

This module contains SJF4J's experimental ASM-based implementation work.

## Project status

Development of the ASM approach is currently paused. It is retained in the
repository for reference and experimentation, but it is not the primary path
for new features and should not be considered the recommended integration
point for production use.

## Direction

Future code-generation work will focus on compile-time code generation in
[`sjf4j-processor`](../sjf4j-processor). This keeps generated code visible at
compile time, integrates naturally with standard Java build tools, and avoids
requiring runtime bytecode generation.

For new integrations or contributions, please prefer `sjf4j-processor` unless
there is a specific reason to investigate the ASM implementation.
