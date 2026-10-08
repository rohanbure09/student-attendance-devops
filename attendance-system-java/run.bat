@echo off
rem Starts the app (builds first if needed). Open http://localhost:8080
cd /d "%~dp0"

if not exist out\attendance.jar call build.bat || exit /b 1
java -jar out\attendance.jar
