#!/usr/bin/env bash
# Builds, then runs the tests. Exit code is non-zero if anything fails.
set -euo pipefail
cd "$(dirname "$0")"

bash build.sh
java -cp out/classes:out/test-classes attendance.TestRunner
