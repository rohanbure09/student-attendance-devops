#!/usr/bin/env bash
# Starts the app (builds first if needed). Open http://localhost:8080
set -euo pipefail
cd "$(dirname "$0")"

[ -f out/attendance.jar ] || bash build.sh
exec java -jar out/attendance.jar
