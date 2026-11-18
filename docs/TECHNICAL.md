# Technical notes

## What CVE-2026-114938 is

CVE-2026-114938 is a memory-corruption vulnerability in the x86-64 HotSpot
Base64 decoder intrinsic. A malformed padded tail can underflow the computed
output length used for the AVX-512 store mask. The affected intrinsic can then
write beyond the output array before Java rejects the invalid Base64.

The practical exposure gate is the intersection of:

1. an affected Java runtime;
2. x86-64 with `avx512f`, `avx512bw`, `avx512vbmi`, and `bmi2` visible to the JVM;
3. `UseBASE64Intrinsics` enabled;
4. attacker-controlled Base64 reaching `java.util.Base64.Decoder` after C2 has
   compiled the hot path.

The Docker image in this repository uses Eclipse Temurin 25.0.5+8 and pins the
multi-architecture image digest. The lab only reproduces on an affected amd64
host exposing the required CPU flags. Docker cannot emulate missing AVX-512
instructions.

## From the Java API to the native write

The public trigger used by the JDK 25 lab is 64 `A` characters followed by one
`=`. The Java decoder calculates a logical output size of 50 bytes for that
malformed 65-byte input.

After C2 has compiled the hot path, HotSpot can replace the Java Base64 block
decoder with an x86-64 intrinsic. The first 64 input bytes produce 48 output
bytes. The native tail handler then receives a one-byte fragment containing
only the invalid padding character.

The vulnerable calculation is equivalent to:

```text
remaining_length = 1
output_size = (remaining_length / 4) * 3
            = 0
output_size = output_size - 1  # trailing '='
            = -1
```

The intrinsic uses this count to create a 64-bit AVX-512 store mask with
`BZHI`. The low byte of `-1` is 255. Because 255 exceeds the 64-bit operand
width, the instruction leaves all mask bits set. The following `VMOVDQU8`
operation therefore stores all 64 vector lanes starting at output offset 48.

```text
logical output: offsets 0..49
vector store:   offsets 48..111
extra write:    offsets 50..111 = 62 bytes
```

The scalar Java path later rejects the malformed input and throws
`IllegalArgumentException`. That exception does not roll back the native write.

JDK 17 uses the 129-byte public trigger (`A` repeated 128 times followed by
`=`). `RuntimeProbe.java` selects the shape from the runtime feature version.

## How the bounded probe contains the machine write

The lab passes a real 4 KiB destination array to the decoder and fills it with
the sentinel byte `0x5a`. It then counts changes beginning at the logical output
size. This design keeps the entire 64-byte machine store inside a valid Java
array while still proving that 62 bytes beyond the logical output changed.

This is deliberately different from allocating only the expected 50 bytes and
trying to corrupt the next heap object. The latter is required for exploitation
research but introduces allocator, garbage collector, object-layout, and crash
variability that the first-run checker does not need.

## Why the endpoint returns an ordinary response

The intrinsic writes before the scalar Java decoder handles the malformed tail.
The Java layer then throws `IllegalArgumentException`. A real authentication
filter can catch that exception and return an ordinary `401`. A response-code
scanner therefore cannot prove safety.

The lab uses a 4 KiB destination array filled with `0x5a`. That keeps every
machine write inside a real Java array while making the extra writes measurable.
The vulnerable container should report bytes changed beyond the output size.
The mitigated container, started with `-XX:-UseBASE64Intrinsics`, is the negative
control and should report zero.

## Evidence levels

| Level | What this repository proves |
| --- | --- |
| Inventory | Runtime version, architecture, and CPU gate |
| Runtime | Bounded write beyond the expected Base64 output size |
| HTTP reachability | An unauthenticated HTTP request reaches the decoder |
| DoS | Not forced by the default demo |
| RCE / shell | Demonstrated by Calif; not reproduced by this repository |

## Why a remote response-code scanner is inconclusive

A scanner outside the JVM cannot directly observe the sentinel array, CPU
features, effective JVM flags, or C2 compilation state. An application may
catch the decoder exception and return `400` or `401` after memory changed. A
different application may return `500` for ordinary validation failure. A
connection reset can indicate a crash, a proxy timeout, or an unrelated
network problem.

For that reason, this repository treats runtime inventory plus the bounded
local A/B probe as strong evidence. Remote status codes alone are not promoted
to an OOB, RCE, or shell verdict.

See [PUBLIC-EXPLOIT-TRACKER.md](PUBLIC-EXPLOIT-TRACKER.md) for the distinction
between the official RCE demonstration, public OOB/crash reproducers, simulator
code, and purpose-built command gates.

## Patch and mitigation

Upgrade using the build provided by your Java vendor. The November 2026 fixed
OpenJDK update lines are 17.0.21.1, 21.0.13.1, 25.0.5.1, and 27.0.1.1.
Oracle's affected list for this CVE also includes the special
`8u511b33-perf` build.

Until the update is deployed and every JVM is restarted, disable the narrow
intrinsic:

```text
-XX:-UseBASE64Intrinsics
```

On x86-64, `-XX:UseAVX=2` also prevents the AVX-512 path, but the narrower
Base64-specific flag is easier to reason about. Test either workaround against
the application before production rollout.

The upstream fix routes a padded tail shorter than four bytes back to the Java
handler before the vulnerable mask calculation:

```diff
 // A padded Base64 tail shorter than one complete block is handled by Java.
+__ cmpl(length, 4);
+__ jcc(Assembler::less, L_bruteForce);
```

After upgrading, rebuild container images and restart the running JVM. A new
package on disk does not change code already loaded into an existing process.
