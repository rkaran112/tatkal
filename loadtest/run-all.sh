#!/usr/bin/env bash
# Runs the whole experiment and writes docs/results/results.csv from scratch.
#
# For every strategy (v1 naive, v2 row lock, v3 redis hold) and every scenario
# (same-seat, spread):
#   - 1 warm-up race that is NOT recorded (the first race against a freshly
#     started server is slower and can fail to connect, which says nothing
#     about the booking method)
#   - 3 recorded races
#
# Usage, from the repo root, with the backend already running:
#   bash loadtest/run-all.sh [base-url] [users]
#
# Note: v1 is EXPECTED to double-book, so its non-zero exit code is ignored here.
# v2 and v3 must never double-book; if one does, this script stops.

BASE_URL="${1:-http://localhost:8080}"
USERS="${2:-1000}"

rm -f docs/results/results.csv

for scenario in same-seat spread; do
  for strategy in v1 v2 v3; do
    echo "=== $strategy / $scenario: warm-up ==="
    java loadtest/LoadTest.java "$BASE_URL" "$strategy" "$USERS" "$scenario" no-csv > /dev/null || true

    for run in 1 2 3; do
      echo "=== $strategy / $scenario: run $run ==="
      java loadtest/LoadTest.java "$BASE_URL" "$strategy" "$USERS" "$scenario" || {
        if [ "$strategy" != "v1" ]; then
          echo "STOP: $strategy double-booked a seat"
          exit 1
        fi
      }
    done
  done
done

echo
echo "Done. Results are in docs/results/results.csv"
