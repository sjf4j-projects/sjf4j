# SJF4J Testbench

## GraalVM Native Image 评测

Native Image 评测使用 JMH。构建时会先以 GraalVM tracing agent 运行一次目标基准，生成反射、资源等运行时配置；随后基于该配置构建 native executable。因此，构建和运行时应使用相同的基准正则。

### 前置条件

- GraalVM for JDK 17，且已安装 `native-image`；
- `GRAALVM_HOME` 指向该 GraalVM，并将其 `bin` 目录加入 `PATH`。

```bash
export GRAALVM_HOME=/path/to/graalvm-jdk-17
export PATH="$GRAALVM_HOME/bin:$PATH"
```

### 构建并运行

以下示例评测 `HandWriteBenchmark`：

```bash
./gradlew :sjf4j-testbench:nativeCompile \
  -PnativeBenchmark='.*HandWriteBenchmark.*'

./sjf4j-testbench/build/native/nativeCompile/sjf4j-benchmark-native \
  '.*HandWriteBenchmark.*'
```

`nativeCompile` 会执行 `jmhAgent`，并将采集的配置写入
`sjf4j-testbench/build/native/agent-output/jmhAgent`。生成的可执行文件位于
`sjf4j-testbench/build/native/nativeCompile/sjf4j-benchmark-native`。

使用其他基准时，将两处 `HandWriteBenchmark` 正则同时替换为目标基准，例如：

```bash
./gradlew :sjf4j-testbench:nativeCompile \
  -PnativeBenchmark='.*JsonReadBenchmark.*'

./sjf4j-testbench/build/native/nativeCompile/sjf4j-benchmark-native \
  '.*JsonReadBenchmark.*'
```

可向 native executable 传递标准 JMH 参数；例如 `-wi 10 -i 10 -w 300ms -r 300ms`。
使用 `-h` 查看完整参数列表。
