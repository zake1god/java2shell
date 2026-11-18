#!/usr/bin/env bash
set -uo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
missing_tools=()
missing_flags=()
cpu_gate="unknown"
docker_daemon="unknown"

for tool in docker curl; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    missing_tools+=("$tool")
  fi
done

arch="$(uname -m 2>/dev/null || printf 'unknown')"

if [[ "$arch" != "x86_64" && "$arch" != "amd64" ]]; then
  cpu_gate="not_x86_64"
elif [[ -r /proc/cpuinfo ]]; then
  cpu_text="$(tr '[:upper:]' '[:lower:]' </proc/cpuinfo)"
  for flag in avx512f avx512bw avx512vbmi bmi2; do
    if [[ "$cpu_text" != *"$flag"* ]]; then
      missing_flags+=("$flag")
    fi
  done
  if (( ${#missing_flags[@]} == 0 )); then
    cpu_gate="supported"
  else
    cpu_gate="missing_$(IFS=,; printf '%s' "${missing_flags[*]}")"
  fi
else
  cpu_gate="unknown_non_linux"
fi

compose="missing"
if command -v docker >/dev/null 2>&1; then
  if docker compose version >/dev/null 2>&1; then
    compose="available"
  else
    missing_tools+=("docker-compose-plugin")
  fi

  if docker info >/dev/null 2>&1; then
    docker_daemon="reachable"
  else
    docker_daemon="unreachable"
  fi
fi

image_pin="$(grep -m1 '^FROM eclipse-temurin:' "$repo_dir/Dockerfile" 2>/dev/null || true)"

printf 'arch=%s\n' "$arch"
printf 'cpu_gate=%s\n' "$cpu_gate"
printf 'docker_compose=%s\n' "$compose"
printf 'docker_daemon=%s\n' "$docker_daemon"
printf 'image_pin=%s\n' "${image_pin:-not_found}"

if (( ${#missing_tools[@]} > 0 )); then
  printf 'missing_prerequisites=%s\n' "$(IFS=,; printf '%s' "${missing_tools[*]}")"
  printf 'preflight_status=blocked_missing_prerequisites\n'
  exit 1
fi

if [[ "$docker_daemon" != "reachable" ]]; then
  printf 'preflight_status=blocked_docker_daemon\n'
  exit 1
fi

case "$cpu_gate" in
  supported)
    printf 'preflight_status=ready\n'
    printf 'next_step=make verify\n'
    exit 0
    ;;
  unknown*)
    printf 'preflight_status=unknown_cpu_gate\n'
    printf 'next_step=run_inside_linux_or_wsl2_and_check_/proc/cpuinfo\n'
    exit 3
    ;;
  *)
    printf 'preflight_status=blocked_cpu_gate\n'
    printf 'next_step=use_an_amd64_host_that_exposes_all_required_flags\n'
    exit 2
    ;;
esac
