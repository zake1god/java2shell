# RCE and shell status

## Short answer

Application-specific RCE has been demonstrated by the discoverer, including a
public Keycloak video. The demonstrated product exploit implementations are
withheld. This repository therefore proves the bounded out-of-bounds write and
its mitigation, but does not claim that a public product shell chain has been
reproduced.

Current repository verdict:

```text
oob_write_reproduced=true
mitigated_control_changed_bytes=0
application_rce_reported=true
public_product_shell_chain_reproduced=false
```

## Why these statements are not contradictory

The evidence exists at different levels:

| Level | Required evidence | Current status |
| --- | --- | --- |
| 1. Reachability | Attacker-controlled input reaches `java.util.Base64.Decoder` | Reproduced locally over HTTP |
| 2. Native corruption | Bytes change after the expected output | Reproduced: 62 bytes |
| 3. Negative control | Identical input does not corrupt with mitigation | Reproduced: zero bytes |
| 4. Process impact | Target JVM crashes or leaks heap data | Reported independently; not forced by this bounded lab |
| 5. Command execution | CVE path causes a benign target-side command | Demonstrated privately by Calif; not reproduced from public product source |
| 6. Interactive shell | A stable command channel is reached through the same CVE chain | Not reproduced here |

A result at one level does not automatically prove the next level. In
particular, `IllegalArgumentException`, HTTP `500`, a connection reset, or a JVM
crash does not prove command execution.

## What the public lab implements

```text
attacker-controlled loopback HTTP body
  -> java.util.Base64.Decoder
  -> C2 AVX-512 intrinsic
  -> 62 bytes changed past expected output
  -> identical mitigated control changes zero bytes
```

The destination is a real 4 KiB Java array filled with a sentinel. The extra
machine write stays inside that array, making the effect measurable without
trying to corrupt an arbitrary neighboring object.

The lab contains no reverse shell, web shell, command parameter, product heap
profile, arbitrary writer, or Code Cache/JVM-hook primitive.

## Public audit performed on 2026-11-18

The refreshed GitHub query returned 54 repositories at 05:45 UTC. Manual review
of the plausible shell candidates still found no independently reproducible
product chain.

Two common false promotions are now documented explicitly:

1. **Separate command-injection sweep.** One public tool sets `shell=1` if a
   generic `cmd`, `exec`, `run`, or callback endpoint accepts a command. Its
   source states that this is not the withheld Java4Shell chain. That may prove
   another command-injection bug, but it does not connect the Base64 OOB write
   to execution.
2. **Purpose-built role bit.** Another lab deliberately places a role byte at a
   known offset and calls `ProcessBuilder("/usr/bin/id")` when the OOB write
   changes it. This is a useful synthetic demonstration of a data-only action,
   but the application itself supplies the command gate. It is not a Keycloak,
   Jenkins, or general OpenJDK shell exploit.

The commit-pinned details are in
[PUBLIC-EXPLOIT-TRACKER.md](PUBLIC-EXPLOIT-TRACKER.md).

## Why this repository does not add a synthetic shell

Adding an endpoint that already calls `ProcessBuilder`, `Runtime.exec`, or
`/bin/sh` would make the screen output more dramatic but the evidence weaker.
The command would be executed by lab code written for that purpose, not by a
publicly reproduced application exploit chain.

The same problem applies to a script that sends the Base64 trigger and then
tests unrelated SSTI, JNDI, or command parameters. If one of those parameters
executes, the result proves the independent injection path.

## Promotion gate for a future public shell

A future artifact may be promoted only when every item below passes:

1. use a credible public exploit and pin its exact upstream commit;
2. audit every downloaded file before execution;
3. bind the vulnerable and control targets to loopback;
4. reproduce from a clean clone on an eligible AVX-512 VBMI host;
5. show a benign target-side marker such as fixed `id` output;
6. prove that the marker was caused by the Base64 corruption chain;
7. show that the identical attempt fails with a patched runtime or
   `-XX:-UseBASE64Intrinsics`;
8. exclude preinstalled command endpoints, synthetic role bits, SSTI/JNDI, and
   other independent execution paths;
9. record the runtime, CPU flags, image digest, garbage collector, commands,
   exit codes, and observed output;
10. keep the artifact local-only and do not make it portable to unrelated
    public targets.

Until that gate passes, the accurate statement is:

> The 62-byte memory corruption is publicly reproducible with a clean negative
> control. Application-specific RCE is demonstrated by the discoverer, but the
> public product shell implementations have not been independently reproduced.
