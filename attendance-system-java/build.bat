@echo off
rem Compiles the app and test code and packages out\attendance.jar (needs JDK 17+).
cd /d "%~dp0"

if exist out rmdir /s /q out
mkdir out\classes
mkdir out\test-classes

javac --release 17 -d out\classes src\main\java\attendance\*.java || exit /b 1
javac --release 17 -cp out\classes -d out\test-classes src\test\java\attendance\*.java || exit /b 1
jar --create --file out\attendance.jar --main-class attendance.Main -C out\classes . || exit /b 1

echo Build OK: out\attendance.jar
