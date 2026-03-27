@echo off
REM Siyo Compiler launcher for Windows
REM Usage: siyoc run file.siyo
REM        siyoc compile file.siyo
REM        siyoc repl

set SCRIPT_DIR=%~dp0
set SIYO_JAR=%SCRIPT_DIR%..\target\siyo-compiler-0.1.0-SNAPSHOT.jar

if not exist "%SIYO_JAR%" (
    echo Error: siyo-compiler JAR not found at %SIYO_JAR%
    echo Run 'mvn package -DskipTests' to build it.
    exit /b 1
)

java -jar "%SIYO_JAR%" %*
