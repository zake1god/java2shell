# Java2Shell / Java4Shell lab

[![CI](https://github.com/zake1god/java2shell/actions/workflows/ci.yml/badge.svg)](https://github.com/zake1god/java2shell/actions/workflows/ci.yml)

Reproducible, local-only checking lab for **CVE-2026-114938**, commonly called
**Java4Shell** and sometimes **Java2Shell**. The vulnerability is an out-of-bounds
write in HotSpot's x86-64 AVX-512 Base64 decoder intrinsic.

> [!CAUTION]
> This repository contains an intentionally vulnerable Java runtime. The
> supplied services bind to `127.0.0.1` only. Never expose them to a public or
> shared network.

## What this repository proves

- exact vulnerable container: Eclipse Temurin `25.0.5+8`, pinned by image digest;
- CPU/runtime checker with a bounded local probe;
- unauthenticated HTTP path into `java.util.Base64.Decoder`;
- measurable writes past the expected output size;
- a negative control using `-XX:-UseBASE64Intrinsics`;
- patch and fleet-checking guidance with source references.

Application-specific RCE is real: Calif published a Keycloak demonstration and
reports successful chains against several products. This repository does
**not** turn that evidence into a false claim that the public 62-byte trigger is
already a shell. The product exploit implementations remain withheld, while
several public repositories currently contain only simulators, crash detectors,
or synthetic command gates. See [RCE and shell status](docs/RCE-STATUS.md) and
the commit-pinned [public exploit tracker](docs/PUBLIC-EXPLOIT-TRACKER.md).

## Exposure gate

All of these conditions must line up:

1. an affected Java runtime;
2. x86-64 CPU with `avx512f`, `avx512bw`, `avx512vbmi`, and `bmi2`;
3. the Base64 intrinsic enabled;
4. attacker-controlled Base64 reaches `java.util.Base64.Decoder` after C2 JIT
   compilation.

ARM64 and x86-64 systems without the required AVX-512 features do not reproduce
this code path. Docker does not emulate missing CPU instructions.

## Quick start

Requirements: Docker Engine with Compose, `curl`, and an affected amd64 host.

```bash
git clone https://github.com/zake1god/java2shell.git
cd java2shell

docker compose up --detach --build
bash scripts/demo.sh
docker compose down
```

Expected result on a supported host:

```text
[vulnerable container]
{"status":"reproduced","runtime":"25.0.5+8-LTS","input_length":65,"expected_output_size":50,"decoder_rejected":true,"changed_past_expected":62,"first_changed":50,"last_changed":111,"claim":"oob_write_only_not_shell"}
[mitigated control]
{"status":"not_reproduced","runtime":"25.0.5+8-LTS","input_length":65,"expected_output_size":50,"decoder_rejected":true,"changed_past_expected":0,"first_changed":-1,"last_changed":-1,"claim":"oob_write_only_not_shell"}
```

This output was observed on 2026-11-18 in WSL2 on an amd64 host exposing all
four required CPU flags.

## Check an installed Java runtime

The checker runs a bounded probe locally. It never contacts a remote host.

```bash
bash scripts/check.sh
```

Exit codes:

| Code | Meaning |
| ---: | --- |
| `0` | vulnerable write not reproduced |
| `1` | checker error or Java missing |
| `2` | writes past the expected output size reproduced |

Observed local example:

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

## Manual HTTP proof

The lab endpoint only accepts the two public bounded trigger shapes. For the
JDK 25 image:

```bash
payload="$(printf 'A%.0s' {1..64})="

curl --request POST \
  --header 'Content-Type: text/plain' \
  --data-binary "$payload" \
  http://127.0.0.1:8080/decode
```

Repeat against port `8081` for the mitigated control.

## Remediation

Upgrade through your JDK or product vendor and restart every JVM. The November
2026 fixed OpenJDK update lines are:

- `17.0.21.1`
- `21.0.13.1`
- `25.0.5.1`
- `27.0.1.1`

Oracle also lists the special `8u511b33-perf` build as affected by this CVE.
Generic Java 8 or 11 installations must not be classified from the major
version alone; follow the vendor advisory.

Temporary narrow mitigation:

```bash
export JAVA_TOOL_OPTIONS="-XX:-UseBASE64Intrinsics"
```

Rebuild containers after updating the base image. Confirm the actual runtime of
the running process rather than trusting an image tag or SBOM alone.

## Repository map

| Path | Purpose |
| --- | --- |
| `app/LabServer.java` | bounded HTTP lab service |
| `tools/RuntimeProbe.java` | local runtime/CPU/probe checker |
| `compose.yaml` | vulnerable and mitigated containers |
| `scripts/demo.sh` | side-by-side HTTP demonstration |
| `tools/crawl_public_repos.py` | read-only GitHub research crawler |
| `docs/TECHNICAL.md` | root cause, exposure gates, and evidence levels |
| `docs/RCE-STATUS.md` | precise boundary between OOB, DoS, and shell claims |
| `docs/PUBLIC-EXPLOIT-TRACKER.md` | public RCE evidence and audited GitHub claims |
| `docs/REFERENCES.md` | primary and high-confidence sources |

## Bahasa Indonesia singkat

Repo ini membantu mengecek CVE-2026-114938 secara lokal dan aman. Jalankan dua
container: satu memakai JDK rentan, satu lagi memakai mitigation flag. Hasil
yang dibuktikan adalah OOB write 62 byte dengan kontrol negatif bersih. RCE
aplikasi-spesifik memang sudah didemonstrasikan, termasuk pada Keycloak, tetapi
source chain tersebut belum dipublikasikan. Tracker publik di repo membedakan
demo RCE, reproducer crash/OOB, simulator, dan command gate sintetis.

Use only on systems you own or are explicitly authorized to test.
