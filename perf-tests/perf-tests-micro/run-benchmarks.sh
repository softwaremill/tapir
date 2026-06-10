#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
TARGET_DIR="$SCRIPT_DIR/target"
TIMESTAMP="$(date +%Y-%m-%d_%H-%M-%S)"
RESULTS_DIR="$TARGET_DIR/results/$TIMESTAMP"

AP_VERSION="4.3"
AP_DIR="$TARGET_DIR/async-profiler-${AP_VERSION}-macos"
AP_LIB="$AP_DIR/lib/libasyncProfiler.dylib"

# Download async-profiler if not present
if [ ! -f "$AP_LIB" ]; then
  echo "Downloading async-profiler $AP_VERSION..."
  mkdir -p "$TARGET_DIR"
  AP_ZIP="$TARGET_DIR/async-profiler.zip"
  curl -sL "https://github.com/async-profiler/async-profiler/releases/download/v${AP_VERSION}/async-profiler-${AP_VERSION}-macos.zip" -o "$AP_ZIP"
  unzip -q "$AP_ZIP" -d "$TARGET_DIR"
  rm "$AP_ZIP"
  echo "async-profiler installed to $AP_DIR"
else
  echo "async-profiler already present at $AP_DIR"
fi

mkdir -p "$RESULTS_DIR"

echo "Running benchmarks..."
cd "$ROOT_DIR"

# Single quotes for sbt command, double quotes around -prof value to preserve semicolons
sbt 'perfTestsMicro3/Jmh/run -wi 5 -i 10 -f 1 -t 1 -prof "async:libPath='"${AP_LIB}"';output=flamegraph;dir='"${RESULTS_DIR}"'"'

echo ""
echo "Results written to $RESULTS_DIR"
echo "Open the HTML files in a browser to view flame graphs."
