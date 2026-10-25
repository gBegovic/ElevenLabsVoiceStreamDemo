#!/usr/bin/env bash
# Runs the TTFA benchmark matrix on the connected device and writes bench/results.csv.
# Needs a debug build installed (./gradlew installDebug) with ELEVENLABS_API_KEY in local.properties.
#
#   ADB=/path/to/adb bench/run_matrix.sh            # all four setups
#   ADB=/path/to/adb bench/run_matrix.sh ws-40      # only the named setups (appends to results.csv)
#
# Stops at the first setup whose batch aborts (e.g. 401 or quota exceeded); it never retries.
set -euo pipefail

ADB=${ADB:-adb}
RUNS=${RUNS:-6}
TIMEOUT_S=${TIMEOUT_S:-600}
ACTIVITY=com.gbegovic.voicedemo/.MainActivity
OUT="$(cd "$(dirname "$0")" && pwd)/results.csv"
HEADER="setup,run,warmup,mode,variant,model,firstByteMs,firstAudioMs,chars,success"

declare -A SETUPS=(
  [http-default]="--es mode http --ez lowLatency false"
  [http-lowlatency]="--es mode http --ez lowLatency true"
  [ws-0]="--es mode ws --ei tokenDelayMs 0"
  [ws-40]="--es mode ws --ei tokenDelayMs 40"
)
ORDER=(http-default http-lowlatency ws-0 ws-40)
[ $# -gt 0 ] && ORDER=("$@")

[ -f "$OUT" ] || echo "$HEADER" > "$OUT"

run_setup() {
  local setup=$1 status=""
  # shellcheck disable=SC2086 # SETUPS values are intentionally word-split into am arguments
  $ADB logcat -c
  $ADB shell am start -S -n "$ACTIVITY" ${SETUPS[$setup]} --ei runs "$RUNS" > /dev/null
  echo "$(date +%T) $setup: started ($RUNS runs)"

  for ((t = 0; t < TIMEOUT_S; t++)); do
    status=$($ADB logcat -d -s TTFA_BATCH:* -v raw | tr -d '\r' | grep -E '^(done|aborted)' | head -1 || true)
    [ -n "$status" ] && break
    sleep 1
  done

  # Run order = logcat order; the first run of each setup is the warm-up.
  $ADB logcat -d -s TTFA_BENCH:* -v raw | tr -d '\r' | grep -v '^-' | grep . |
    awk -v s="$setup" '{ n++; printf "%s,%d,%s,%s\n", s, n, (n == 1 ? "true" : "false"), $0 }' >> "$OUT"

  echo "$(date +%T) $setup: ${status:-timed out after ${TIMEOUT_S}s}"
  [[ $status == done ]]
}

for setup in "${ORDER[@]}"; do
  [ -n "${SETUPS[$setup]:-}" ] || { echo "unknown setup: $setup" >&2; exit 2; }
  run_setup "$setup" || { echo "stopping the matrix: $setup did not finish cleanly" >&2; exit 1; }
done
echo "results: $OUT"
