# Troubleshooting

Start with:

```bash
make preflight
```

Do not remove a failed preflight gate merely to make the demo print
`reproduced`. The gate identifies whether the host can execute the affected
native path.

## Both containers report zero changed bytes

Most common causes:

1. the host is ARM64;
2. one or more of `avx512f`, `avx512bw`, `avx512vbmi`, or `bmi2` is missing;
3. a VM or WSL layer does not expose the host feature;
4. the runtime was patched or the intrinsic was disabled.

Check:

```bash
uname -m
grep -m1 '^flags' /proc/cpuinfo | tr ' ' '\n' \
  | grep -E '^(avx512f|avx512bw|avx512vbmi|bmi2)$'
```

Docker cannot emulate a missing CPU feature. Use another compatible amd64 host.

## The vulnerable side reports zero but the CPU gate is supported

Confirm the exact image and runtime:

```bash
docker compose images
docker compose exec vulnerable java -version
docker compose logs vulnerable
```

Then rebuild without relying on an old local tag:

```bash
docker compose down --remove-orphans
docker compose build --no-cache
make verify
```

The `Dockerfile` pins the vulnerable image by digest. If the digest cannot be
pulled, do not substitute an arbitrary image and keep the same expected output.

## Both containers report 62 changed bytes

The mitigated JVM probably did not receive the flag. Inspect it:

```bash
docker compose exec mitigated java -XX:+PrintFlagsFinal -version 2>&1 \
  | grep UseBASE64Intrinsics
```

Expected effective value:

```text
UseBASE64Intrinsics = false
```

Also confirm that `JAVA_TOOL_OPTIONS=-XX:-UseBASE64Intrinsics` remains present
under the `mitigated` service in `compose.yaml`.

## `make check` exits with code 2

This is the intended positive result:

```text
result=VULNERABLE_BEHAVIOR_REPRODUCED
```

Exit `2` means the checker observed bytes changing after the expected output.
It does not mean the checker crashed, and it does not mean a shell was opened.

## `docker compose` is not available

Install the Docker Compose plugin and verify:

```bash
docker compose version
```

The repository uses the modern `docker compose` command, not the legacy
standalone `docker-compose` binary.

## Docker daemon is unreachable

Typical error:

```text
Cannot connect to the Docker daemon
```

Start Docker Engine or Docker Desktop. On WSL2, enable integration for the
Linux distribution and verify `docker info` inside that distribution.

## Port 8080 or 8081 is already in use

Identify the listener before stopping anything:

```bash
ss -ltnp | grep -E ':8080|:8081'
docker ps --format 'table {{.Names}}\t{{.Ports}}'
```

Do not change the Compose binding to a public interface. Stop the conflicting
local service or use an isolated test host.

## The service does not become ready

Collect logs:

```bash
docker compose ps
docker compose logs vulnerable
docker compose logs mitigated
```

Confirm the containers did not fail because of architecture mismatch, an
unsupported JVM option, or memory pressure.

## A remote request returned 400, 401, or 500

None of these status codes proves or disproves CVE-2026-114938. The Java layer
can catch `IllegalArgumentException` after the native write and return an
ordinary authentication error. Conversely, a `500` can come from unrelated
input validation.

Use local runtime inventory and the bounded A/B probe. Do not label a remote
status code as overflow or shell evidence.

## A public tool prints `shell=1`

Inspect how that field is set. Current public examples may mark `shell=1` when
they find a separate command-injection endpoint or receive a callback from a
listener. That proves the independent endpoint, not the Java4Shell memory
corruption chain.

A valid Java4Shell shell result must show that:

1. the vulnerable decoder path caused a benign command marker;
2. no preinstalled command endpoint or synthetic role-bit gate performed it;
3. the same attempt failed against a patched or mitigated control;
4. runtime, CPU, image digest, commands, and target-side output were recorded.

See [RCE-STATUS.md](RCE-STATUS.md) and
[PUBLIC-EXPLOIT-TRACKER.md](PUBLIC-EXPLOIT-TRACKER.md).

## Clean up after an interrupted run

```bash
docker compose down --remove-orphans
```

The supplied services use named Compose resources only and do not require a
recursive filesystem cleanup.

## Opening an issue

Include:

- repository commit;
- runtime and vendor;
- host architecture and CPU flags;
- Docker and Compose versions;
- full preflight output;
- vulnerable and mitigated JSON;
- whether the result came from a clean clone.

Remove tokens, private hostnames, credentials, and third-party data before
posting.
