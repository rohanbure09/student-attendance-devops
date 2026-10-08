#!/usr/bin/env bash
# Compiles the app and test code and packages out/attendance.jar (needs JDK 17+, nothing else).
set -euo pipefail
cd "$(dirname "$0")"

rm -rf out
mkdir -p out/classes out/test-classes

javac --release 17 -d out/classes src/main/java/attendance/*.java
javac --release 17 -cp out/classes -d out/test-classes src/test/java/attendance/*.java
jar --create --file out/attendance.jar --main-class attendance.Main -C out/classes .

echo "Build OK: out/attendance.jar"
