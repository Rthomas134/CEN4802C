#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# monitor.sh -- samples the Task Tracker container's resource usage
# (docker stats) and the app's own status (GET /health) at a fixed
# interval, prints one row per sample, saves them to a CSV file, and prints
# a summary (average and peak) at the end.
#
# Usage (Git Bash, macOS, Linux):
#   ./scripts/monitor.sh baseline            # 60 s, one sample every 5 s
#   ./scripts/monitor.sh workload 90 5       # 90 s, every 5 s
#
# Environment overrides:
#   CONTAINER=task-tracker  BASE_URL=http://localhost:8080
# Output: monitoring-data/<label>.csv   (git-ignored)
# ---------------------------------------------------------------------------
set -u

LABEL="${1:-sample}"
DURATION="${2:-60}"
INTERVAL="${3:-5}"
CONTAINER="${CONTAINER:-task-tracker}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
OUT_DIR="monitoring-data"
CSV="$OUT_DIR/$LABEL.csv"

mkdir -p "$OUT_DIR"

if ! docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null | grep -q true; then
  echo "Container '$CONTAINER' is not running. Start it first (see README)." >&2
  exit 1
fi

# Pull one numeric field out of the /health JSON without needing jq.
json_num() { echo "$2" | grep -o "\"$1\":[-0-9.]*" | head -1 | cut -d: -f2; }

# Convert a docker memory string such as 45.3MiB / 1.2GiB / 900KiB to MiB.
to_mib() {
  echo "$1" | awk '{
    v = $1; u = $1; gsub(/[A-Za-z]/, "", v); gsub(/[0-9.]/, "", u);
    if (u == "GiB" || u == "GB") v *= 1024; else if (u == "KiB" || u == "kB") v /= 1024; else if (u == "B") v /= 1048576;
    printf "%.1f", v }'
}

echo "time,cpu_pct,mem_used_mib,mem_limit_mib,mem_pct,pids,heap_used_mib,heap_committed_mib,threads,requests_total,avg_request_ms,tasks_total,gc_collections" > "$CSV"
printf "Monitoring container '%s' for %ss (every %ss) -> %s\n\n" "$CONTAINER" "$DURATION" "$INTERVAL" "$CSV"
printf "%-8s %7s %10s %7s %5s | %9s %8s %8s %8s %6s %5s\n" \
  "time" "CPU %" "Mem (MiB)" "Mem %" "PIDs" "Heap MiB" "Threads" "Requests" "Avg ms" "Tasks" "GCs"

end=$((SECONDS + DURATION))
while (( SECONDS < end )); do
  stats=$(docker stats --no-stream --format '{{.CPUPerc}};{{.MemUsage}};{{.MemPerc}};{{.PIDs}}' "$CONTAINER")
  health=$(curl -s --max-time 3 "$BASE_URL/health")
  now=$(date +%H:%M:%S)

  cpu=$(echo "$stats" | cut -d';' -f1 | tr -d '%')
  mem_used=$(to_mib "$(echo "$stats" | cut -d';' -f2 | awk -F' / ' '{print $1}')")
  mem_limit=$(to_mib "$(echo "$stats" | cut -d';' -f2 | awk -F' / ' '{print $2}')")
  mem_pct=$(echo "$stats" | cut -d';' -f3 | tr -d '%')
  pids=$(echo "$stats" | cut -d';' -f4)

  if [[ -z "$health" ]]; then
    heap=""; heapc=""; thr=""; reqs=""; avg=""; tasks=""; gcs=""
    status="(no /health response)"
  else
    heap=$(json_num heap_used_mib "$health"); heapc=$(json_num heap_committed_mib "$health")
    thr=$(json_num threads_live "$health"); reqs=$(echo "$health" | grep -o '"requests":{"total":[0-9]*' | grep -o '[0-9]*$')
    avg=$(json_num avg_ms "$health"); tasks=$(echo "$health" | grep -o '"tasks":{"total":[0-9]*' | grep -o '[0-9]*$')
    gcs=$(json_num gc_collections "$health"); status=""
  fi

  echo "$now,$cpu,$mem_used,$mem_limit,$mem_pct,$pids,$heap,$heapc,$thr,$reqs,$avg,$tasks,$gcs" >> "$CSV"
  printf "%-8s %7s %10s %7s %5s | %9s %8s %8s %8s %6s %5s %s\n" \
    "$now" "$cpu" "$mem_used" "$mem_pct" "$pids" "$heap" "$thr" "$reqs" "$avg" "$tasks" "$gcs" "$status"
  sleep "$INTERVAL"
done

echo
awk -F, 'NR > 1 && $2 != "" {
  n++; cpu += $2; mem += $3; heap += $7
  if ($2 > maxcpu) maxcpu = $2; if ($3 > maxmem) maxmem = $3; if ($7 > maxheap) maxheap = $7
  if (n == 1) { r0 = $10 } r1 = $10
} END {
  if (n == 0) { print "No samples collected."; exit }
  printf "Summary of %d samples:\n", n
  printf "  CPU:            avg %6.2f %%    peak %6.2f %%\n", cpu / n, maxcpu
  printf "  Container mem:  avg %6.1f MiB  peak %6.1f MiB\n", mem / n, maxmem
  printf "  JVM heap used:  avg %6.1f MiB  peak %6.1f MiB\n", heap / n, maxheap
  printf "  Requests served during window: %d\n", r1 - r0
}' "$CSV"
