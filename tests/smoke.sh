#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
build_dir="$(mktemp -d)"
trap 'rm -rf -- "$build_dir"' EXIT

bash -n \
  "$repo_dir/scripts/check.sh" \
  "$repo_dir/scripts/demo.sh" \
  "$repo_dir/scripts/preflight.sh" \
  "$repo_dir/scripts/verify.sh"

javac --release 17 -d "$build_dir" "$repo_dir/app/LabServer.java"
javac --release 17 -d "$build_dir" "$repo_dir/tools/RuntimeProbe.java"

java -cp "$build_dir" RuntimeProbe >"$build_dir/probe.out" 2>&1 || probe_status=$?
probe_status="${probe_status:-0}"

if [[ "$probe_status" != "0" && "$probe_status" != "2" ]]; then
  cat "$build_dir/probe.out"
  echo "unexpected RuntimeProbe exit status: $probe_status" >&2
  exit 1
fi

grep -q '^runtime=' "$build_dir/probe.out"
grep -q '^cpu_gate=' "$build_dir/probe.out"
grep -q '^version_assessment=' "$build_dir/probe.out"
grep -Eq '^result=(NOT_REPRODUCED|VULNERABLE_BEHAVIOR_REPRODUCED)$' "$build_dir/probe.out"

echo "smoke test passed (probe exit=$probe_status)"
