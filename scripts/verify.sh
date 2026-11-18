#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"

cleanup() {
  docker compose down --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "[1/4] Checking prerequisites and CPU gate"
bash scripts/preflight.sh

echo
echo "[2/4] Building and starting loopback-only controls"
docker compose up --detach --build

echo
echo "[3/4] Running vulnerable-versus-mitigated comparison"
bash scripts/demo.sh

echo
echo "[4/4] Stopping the lab"
cleanup
trap - EXIT

echo
echo "verification_complete=true"
echo "evidence_level=bounded_oob_write_with_negative_control"
echo "shell_proven=false"
