#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! command -v java >/dev/null 2>&1; then
  echo "result=UNKNOWN"
  echo "reason=java_not_found"
  exit 1
fi

set +e
java "$repo_dir/tools/RuntimeProbe.java"
probe_status=$?
set -e

case "$probe_status" in
  0)
    echo "summary=The bounded runtime probe did not reproduce the vulnerable write."
    ;;
  2)
    echo "summary=The bounded runtime probe reproduced writes past the expected output size."
    ;;
  *)
    echo "summary=The runtime probe failed; inspect the output above."
    ;;
esac

exit "$probe_status"
