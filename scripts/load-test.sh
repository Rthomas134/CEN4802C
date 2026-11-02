#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# load-test.sh -- generates a sustained workload against the Task Tracker:
# several concurrent "users" repeatedly load the page, add tasks and
# complete tasks for a fixed time. Run scripts/monitor.sh in another
# window at the same time to measure the effect.
#
# Usage (Git Bash, macOS, Linux):
#   ./scripts/load-test.sh                # 8 workers for 60 s
#   ./scripts/load-test.sh 16 90          # 16 workers for 90 s
#   BASE_URL=http://localhost:30080 ./scripts/load-test.sh
# ---------------------------------------------------------------------------
set -u

WORKERS="${1:-8}"
DURATION="${2:-60}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
TMP_DIR="$(mktemp -d)"

if ! curl -s -o /dev/null --max-time 3 "$BASE_URL/"; then
  echo "Task Tracker is not reachable at $BASE_URL -- start it first." >&2
  exit 1
fi

worker() {
  local w=$1 count=0 fails=0 end=$((SECONDS + DURATION)) code
  while (( SECONDS < end )); do
    for req in "GET /" "POST /add" "GET /" "POST /complete"; do
      case "$req" in
        "GET /")          code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/") ;;
        "POST /add")      code=$(curl -s -o /dev/null -w '%{http_code}' --data "description=Load+test+task+w${w}-${count}&priority=MEDIUM" "$BASE_URL/add") ;;
        "POST /complete") code=$(curl -s -o /dev/null -w '%{http_code}' --data "id=$((RANDOM % 500 + 1))" "$BASE_URL/complete") ;;
      esac
      count=$((count + 1))
      [[ "$code" == 2* || "$code" == 3* ]] || fails=$((fails + 1))
    done
  done
  echo "$count $fails" > "$TMP_DIR/w$w"
}

echo "Starting $WORKERS workers for ${DURATION}s against $BASE_URL ($(date +%H:%M:%S))"
for ((w = 1; w <= WORKERS; w++)); do worker "$w" & done
wait

total=0; failed=0
for f in "$TMP_DIR"/w*; do read -r c x < "$f"; total=$((total + c)); failed=$((failed + x)); done
rm -rf "$TMP_DIR"
echo "Finished at $(date +%H:%M:%S): $total requests in ${DURATION}s" \
     "($(awk -v t="$total" -v d="$DURATION" 'BEGIN { printf "%.1f", t / d }') req/s), $failed failed"
