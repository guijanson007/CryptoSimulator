#!/usr/bin/env bash
# Benchmarks the price-chart query (CryptoSQL.getPriceHistory) with and without an index on
# Crypto_History (crypto_id, time_stamp), at three history sizes derived from the real bot rates.
# Usage: bench/chart-query.sh            (RUNS, WARMUP_S, MEASURE_S and COIN can be overridden)
# Writes bench/results/<timestamp>.md. Decisions: TESTING_DECISIONS.md D48-D52.
set -euo pipefail

RUNS=${RUNS:-3}
WARMUP_S=${WARMUP_S:-5}
MEASURE_S=${MEASURE_S:-30}
COIN=${COIN:-LTC}                  # busiest default coin (volatility 3.5): the worst case
SIZES_DAYS=(1 7 30)
IMAGE=docker.io/library/postgres:18
NAME=cryptobros-bench

cd "$(dirname "$0")/.."
CLI=${CONTAINER_CLI:-$(command -v podman || command -v docker || true)}
[ -n "$CLI" ] || { echo "podman or docker is required" >&2; exit 1; }

mkdir -p bench/results
OUT=bench/results/$(date +%Y-%m-%d-%H%M).md
trap '"$CLI" rm -f "$NAME" >/dev/null 2>&1 || true' EXIT

psql_db() {
    "$CLI" exec -i -e PGOPTIONS='-c client_min_messages=warning' "$NAME" \
        psql -X -q -v ON_ERROR_STOP=1 -U postgres -d bench "$@"
}

start_db() {
    "$CLI" rm -f "$NAME" >/dev/null 2>&1 || true
    "$CLI" run -d --name "$NAME" -e POSTGRES_PASSWORD=bench -e POSTGRES_DB=bench "$IMAGE" >/dev/null
    # TCP check: the image's init phase only listens on the Unix socket, so this waits for the real server
    until "$CLI" exec "$NAME" pg_isready -q -h 127.0.0.1 -U postgres -d bench; do sleep 1; done
    psql_db < createTables.sql >/dev/null
    "$CLI" exec -i "$NAME" sh -c 'cat > /tmp/chart-query.sql' < bench/chart-query.sql
}

# Reads latencies in microseconds on stdin; prints "n | min | median | p95 | max" in ms (nearest-rank percentiles)
stats() {
    sort -n | awk '{ a[NR] = $1 }
        END { p95 = int(NR * 0.95); if (p95 < NR * 0.95) p95++;
              printf "%d | %.3f | %.3f | %.3f | %.3f", NR, a[1]/1000, a[int((NR+1)/2)]/1000, a[p95]/1000, a[NR]/1000 }'
}

measure() {   # $1 = days, $2 = "no" or "yes" (index)
    psql_db -v days="$1" < bench/generate-history.sql >/dev/null
    if [ "$2" = yes ]; then
        psql_db -c "CREATE INDEX crypto_history_coin_time ON Crypto_History (crypto_id, time_stamp); ANALYZE Crypto_History;"
    fi
    local pgbench=(pgbench -n -U postgres -M prepared -D coin="$COIN" -f /tmp/chart-query.sql)
    "$CLI" exec "$NAME" "${pgbench[@]}" -T "$WARMUP_S" bench >/dev/null
    local result
    result=$("$CLI" exec "$NAME" sh -c "cd /tmp && rm -f pgbench_log.* && $(printf '%q ' "${pgbench[@]}") -T $MEASURE_S -l bench >/dev/null && cat pgbench_log.*" \
        | awk '{ print $3 }' | stats)
    local rows_table rows_returned
    rows_table=$(psql_db -At -c "SELECT count(*) FROM Crypto_History")
    rows_returned=$(psql_db -At -c "SELECT count(*) FROM Crypto_History WHERE crypto_id = '$COIN' AND time_stamp >= CURRENT_TIMESTAMP - INTERVAL '10 minutes'")
    echo "| $run | $1 | $rows_table | $2 | $rows_returned | $result |" >> "$OUT"
    if [ "$run" = 1 ]; then
        { echo; echo "#### Plan: $1 day(s), index: $2"; echo '```';
          psql_db -c "PREPARE q(text) AS $(tr '\n' ' ' < bench/chart-query.sql | sed 's/:coin/$1/; s/;//');
                      EXPLAIN (ANALYZE, BUFFERS) EXECUTE q('$COIN');";
          echo '```'; } >> "$OUT.plans"
    fi
}

{
    echo "# Chart query benchmark, $(date '+%Y-%m-%d %H:%M %Z')"
    echo
    echo "## Environment"
    echo "- Commit: $(git rev-parse --short HEAD)$(git diff --quiet || echo ' (uncommitted changes)')"
    echo "- CPU: $(lscpu | sed -n 's/^Model name: *//p'), $(nproc) logical CPUs"
    echo "- CPU governor: $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null || echo unknown)"
    echo "- On AC power: $(cat /sys/class/power_supply/A*/online 2>/dev/null | head -1 | sed 's/1/yes/; s/0/no/' || echo unknown)"
    echo "- RAM: $(free -h | awk '/^Mem:/ { print $2 }')"
    echo "- OS: $(. /etc/os-release && echo "$PRETTY_NAME"), kernel $(uname -r)"
    echo "- Container runtime: $("$CLI" --version)"
    echo "- Client and server: same machine; pgbench runs inside the PostgreSQL container (no network)"
    echo "- Parameters: RUNS=$RUNS, WARMUP_S=$WARMUP_S, MEASURE_S=$MEASURE_S, COIN=$COIN, cache: warm only"
} > "$OUT"

for run in $(seq 1 "$RUNS"); do
    echo "Run $run/$RUNS: starting a fresh container" >&2
    start_db
    if [ "$run" = 1 ]; then
        echo "- PostgreSQL: $(psql_db -At -c 'SHOW server_version')" >> "$OUT"
        { echo; echo "## Results (latency in ms, single client)"; echo;
          echo "| Run | Days | Rows in table | Index | Rows returned | Samples | Min | Median | p95 | Max |";
          echo "|---|---|---|---|---|---|---|---|---|---|"; } >> "$OUT"
    fi
    for days in "${SIZES_DAYS[@]}"; do
        for index in no yes; do
            echo "  $days day(s), index: $index" >&2
            measure "$days" "$index"
        done
    done
done

{ echo; echo "## Query plans (run 1)"; cat "$OUT.plans"; } >> "$OUT"
rm -f "$OUT.plans"
echo "Results written to $OUT" >&2
