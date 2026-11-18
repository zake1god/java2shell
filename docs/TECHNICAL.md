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
