#!/usr/bin/env bash
# Compiles the Minecraft-independent core and runs ALL stages' tests together (no network needed).
set -euo pipefail
cd "$(dirname "$0")"
rm -rf build && mkdir -p build/main build/test
javac -Xlint:all -d build/main $(find core/src/main -name '*.java')
javac -Xlint:all -cp build/main -d build/test $(find core/src/test -name '*.java')
java -cp build/main:build/test client.paint.core.AllTests
