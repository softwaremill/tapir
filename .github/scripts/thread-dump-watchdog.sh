#!/usr/bin/env bash
# Usage: thread-dump-watchdog.sh <log-file> <silence-seconds>
# Run in the background next to a build whose output is written to <log-file>. Whenever the log hasn't been written
# to for more than <silence-seconds>, appends the process tree and thread dumps of all JVMs to thread-dumps.log
# (uploaded as a CI artifact), so that silent hangs can be diagnosed. Runs until killed.

log_file=$1
silence_seconds=$2

touch "$log_file"
while true; do
  sleep 30
  if [ $(( $(date +%s) - $(stat -c %Y "$log_file") )) -gt "$silence_seconds" ]; then
    {
      echo "=== $(date -u '+%Y-%m-%d %H:%M:%S') $log_file silent for >${silence_seconds}s, dumping threads ==="
      ps -ef --forest
      for pid in $(jps -q); do
        echo "--- jcmd $pid Thread.print ---"
        jcmd "$pid" Thread.print || true
      done
    } >> thread-dumps.log 2>&1
  fi
done
