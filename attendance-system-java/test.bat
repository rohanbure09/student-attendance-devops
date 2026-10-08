@echo off
rem Builds, then runs the tests.
cd /d "%~dp0"

call build.bat || exit /b 1
java -cp out\classes;out\test-classes attendance.TestRunner
