# JIT Inlining & Compilation Analysis

Tools and flags for understanding how the JIT compiler optimizes the SBE vs sbe4kt benchmarks.

All commands assume you've already built the benchmark JAR:
```bash
./gradlew :sbe-benchmarks:jar
```

Base JVM flags used throughout:
```
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED
-Dagrona.disable.bounds.checks=true
```

---

## 1. PrintInlining — What Got Inlined

Shows every inlining decision: what was inlined, what wasn't, and why.

```bash
java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
  -Dagrona.disable.bounds.checks=true \
  -XX:+UnlockDiagnosticVMOptions \
  -XX:+PrintInlining \
  -jar sbe-benchmarks/build/libs/sbe-benchmarks.jar \
  -f 1 -wi 3 -i 1 -tu ns \
  "FixedMessageBenchmark.testSbe4ktEncode"
```

Filter for relevant methods:
```bash
... 2>&1 | grep -E "encode|decode|FixedMessage|Codec|putInt|putLong|putByte|getInt|getLong|getByte"
```

### Reading the output

```
@ 42   sbe4kt.example.FixedMessageCodec.fieldLong1 (18 bytes)   inline (hot)
@ 15   org.agrona.concurrent.UnsafeBuffer.putLong (20 bytes)     inline (hot)
@ 98   sbe4kt.example.FixedMessageCodec.fieldChar (15 bytes)     not inline (too big)
```

- `inline (hot)` — inlined successfully
- `not inline (too big)` — method body exceeded `MaxInlineSize` (default 35 bytes) or `FreqInlineSize` (default 325 bytes)
- `not inline (no static binding)` — virtual dispatch, JIT couldn't determine the target
- `not inline (exec count < MinInliningThreshold)` — not called often enough

### Tuning inlining thresholds

```bash
# Show current defaults
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintFlagsFinal 2>&1 | grep -i inline

# Key flags
-XX:MaxInlineSize=35          # max bytecode size for unconditional inlining
-XX:FreqInlineSize=325        # max bytecode size for hot methods
-XX:InlineSmallCode=2000      # max native code size for inlining
-XX:MaxInlineLevel=15         # max depth of inlining chain
```

---

## 2. JMH Profilers

### Compilation profiler
```bash
java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
  -Dagrona.disable.bounds.checks=true \
  -jar sbe-benchmarks/build/libs/sbe-benchmarks.jar \
  -f 1 -wi 3 -i 3 -tu ns \
  -prof comp \
  "FixedMessageBenchmark"
```

### Annotated assembly (perfasm)

Shows hottest regions of generated native code with source mapping:

```bash
java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
  -Dagrona.disable.bounds.checks=true \
  -jar sbe-benchmarks/build/libs/sbe-benchmarks.jar \
  -f 1 -wi 3 -i 3 -tu ns \
  -prof perfasm \
  "FixedMessageBenchmark.testSbe4ktEncode"
```

On macOS, you may need:
```bash
-prof perfasm:events=cpu-clock
# or
-prof dtraceasm
```

### List all available profilers
```bash
java -jar sbe-benchmarks/build/libs/sbe-benchmarks.jar -lprof
```

---

## 3. PrintAssembly — Raw Native Code

See the actual machine code the JIT produced for specific methods.

```bash
java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
  -Dagrona.disable.bounds.checks=true \
  -XX:+UnlockDiagnosticVMOptions \
  -XX:+PrintAssembly \
  -XX:PrintAssemblyOptions=intel \
  -jar sbe-benchmarks/build/libs/sbe-benchmarks.jar \
  -f 1 -wi 3 -i 1 -tu ns \
  "FixedMessageBenchmark.testSbe4ktEncode"
```

Filter to specific methods:
```bash
-XX:CompileCommand=print,*FixedMessageBenchmark.encodeFw
-XX:CompileCommand=print,*FixedMessageBenchmark.encodeSbe
```

Requires the `hsdis` disassembler. Check if available:
```bash
find $JAVA_HOME -name "hsdis*" 2>/dev/null
```

---

## 4. LogCompilation + JITWatch — Visual Analysis

### Generate the log
```bash
java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
  -Dagrona.disable.bounds.checks=true \
  -XX:+UnlockDiagnosticVMOptions \
  -XX:+LogCompilation \
  -XX:LogFile=jit.log \
  -jar sbe-benchmarks/build/libs/sbe-benchmarks.jar \
  -f 1 -wi 3 -i 1 -tu ns \
  "FixedMessageBenchmark"
```

### View with JITWatch

```bash
brew install jitwatch
# or download from https://github.com/AdoptOpenJDK/jitwatch/releases
```

JITWatch shows:
- Inlining tree per compiled method
- Bytecode vs native assembly side-by-side
- Why methods were not inlined
- Compilation timeline and tiers (C1 → C2)

---

## 5. Ideal Graph Visualizer — Compiler IR

For deep-diving into the C2 compiler's optimization passes:

```bash
java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
  -Dagrona.disable.bounds.checks=true \
  -XX:+UnlockDiagnosticVMOptions \
  -Dgraal.Dump=:3 \
  -jar sbe-benchmarks/build/libs/sbe-benchmarks.jar \
  -f 0 -wi 3 -i 1 -tu ns \
  "FixedMessageBenchmark.testSbe4ktEncode"
```

Download IGV from https://www.graalvm.org/latest/tools/igv/

---

## Quick Reference: Comparing SBE vs sbe4kt

### Side-by-side inlining comparison
```bash
# sbe4kt encode
java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
  -Dagrona.disable.bounds.checks=true \
  -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining \
  -jar sbe-benchmarks/build/libs/sbe-benchmarks.jar \
  -f 1 -wi 3 -i 1 -tu ns \
  "FixedMessageBenchmark.testSbe4ktEncode" 2>&1 | tee sbe4kt_encode_inlining.txt

# SBE encode
java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
  -Dagrona.disable.bounds.checks=true \
  -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining \
  -jar sbe-benchmarks/build/libs/sbe-benchmarks.jar \
  -f 1 -wi 3 -i 1 -tu ns \
  "FixedMessageBenchmark.testSbeEncode" 2>&1 | tee sbe_encode_inlining.txt

# Compare
diff <(grep "inline" sbe4kt_encode_inlining.txt | head -50) \
     <(grep "inline" sbe_encode_inlining.txt | head -50)
```

### Side-by-side assembly
```bash
# sbe4kt encode assembly
java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
  -Dagrona.disable.bounds.checks=true \
  -jar sbe-benchmarks/build/libs/sbe-benchmarks.jar \
  -f 1 -wi 3 -i 3 -tu ns \
  -prof perfasm:tooBigThreshold=1500 \
  "FixedMessageBenchmark.testSbe4ktEncode" 2>&1 | tee sbe4kt_encode_asm.txt

# SBE encode assembly
java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
  -Dagrona.disable.bounds.checks=true \
  -jar sbe-benchmarks/build/libs/sbe-benchmarks.jar \
  -f 1 -wi 3 -i 3 -tu ns \
  -prof perfasm:tooBigThreshold=1500 \
  "FixedMessageBenchmark.testSbeEncode" 2>&1 | tee sbe_encode_asm.txt
```

### What to look for

| Symptom | Likely cause |
|---|---|
| sbe4kt methods not inlined | Kotlin compiler generates larger bytecode (null checks, bridge methods) |
| Extra null checks in assembly | Kotlin non-null assertions (`Intrinsics.checkNotNull`) |
| Virtual dispatch (`callq`) | Interface/abstract method calls not devirtualized |
| Identical assembly for both | Both fully optimized — performance difference is elsewhere |
| `uncommon_trap` in assembly | Speculative optimization with deopt guard |
