#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# generate-traffic.sh -- drives realistic HTTP traffic at a running Task
# Tracker so the application produces a large, varied set of log entries.
#
# This script does NOT write any log lines itself. It only sends requests
# (the same ones the browser form sends); every log entry is produced by
# the application while handling them.
#
# Usage (Git Bash, macOS, Linux):
#   ./scripts/generate-traffic.sh                 # 25 simulated user sessions
#   ./scripts/generate-traffic.sh 60              # 60 sessions
#   BASE_URL=http://localhost:30080 ./scripts/generate-traffic.sh   # Kubernetes NodePort
# ---------------------------------------------------------------------------
set -u

BASE_URL="${BASE_URL:-http://localhost:8080}"
SESSIONS="${1:-25}"

TASKS=("Buy groceries" "Finish CEN4802 assignment" "Call the dentist" "Pay electric bill"
       "Walk the dog" "Study for statistics exam" "Renew car registration" "Clean the kitchen"
       "Email professor" "Update resume" "Book flight" "Water the plants" "Back up laptop"
       "Schedule oil change" "Read chapter 7")
PRIORITIES=("HIGH" "MEDIUM" "LOW" "HIGH" "MEDIUM" "LOW" "urgent" "")   # last two are bad input

next_id=1   # the app assigns ids sequentially starting at 1 (per server run)
requests=0

post() {  # post <path> <url-encoded body>
  curl -s -o /dev/null -X POST --data "$2" "$BASE_URL$1"
  requests=$((requests + 1))
}
get() {   # get <path>
  curl -s -o /dev/null "$BASE_URL$1"
  requests=$((requests + 1))
}

if ! curl -s -o /dev/null "$BASE_URL/"; then
  echo "Task Tracker is not reachable at $BASE_URL -- start it first." >&2
  exit 1
fi
requests=1

for ((s = 1; s <= SESSIONS; s++)); do
  get "/"                                            # user opens the page

  # Add 1-3 tasks (the browser follows each redirect with another GET /)
  for ((i = 0; i < RANDOM % 3 + 1; i++)); do
    task="${TASKS[RANDOM % ${#TASKS[@]}]}"
    prio="${PRIORITIES[RANDOM % ${#PRIORITIES[@]}]}"
    post "/add" "description=${task// /+}&priority=$prio"
    next_id=$((next_id + 1))
    get "/"
  done

  # Complete and delete something that (probably) exists
  if (( next_id > 1 )); then
    post "/complete" "id=$((RANDOM % (next_id - 1) + 1))"; get "/"
  fi
  if (( RANDOM % 3 == 0 && next_id > 1 )); then
    post "/delete" "id=$((RANDOM % (next_id - 1) + 1))"; get "/"
  fi

  # Occasional mistakes a real client could make
  case $((RANDOM % 8)) in
    0) post "/add" "description=+++&priority=HIGH" ;;          # blank description
    1) post "/complete" "id=9999" ;;                            # unknown id
    2) post "/delete" "id=abc" ;;                               # malformed id
    3) get "/favicon.ico" ;;                                    # unknown path -> 404
    4) get "/add" ;;                                            # wrong method -> 405
    *) ;;                                                       # no mistake this time
  esac
done

# One request from a client that forgot to URL-encode a "%" ("50% off").
# The JDK's URLDecoder throws IllegalArgumentException on the bare "%"
# -> SEVERE entry with stack trace + HTTP 500 page.
post "/add" "description=50%+off+sale&priority=LOW"

echo "Sent $requests requests to $BASE_URL"
