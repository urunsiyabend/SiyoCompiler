@echo off
set SCRIPT_DIR=%~dp0
set SIYO_JAR=%SCRIPT_DIR%..\target\siyo-compiler-0.1.0-SNAPSHOT-shaded.jar

if not exist "%SIYO_JAR%" (
    echo Error: siyo-compiler JAR not found. Run 'mvn package -DskipTests'
    exit /b 1
)

REM Parse -cp flag
set EXTRA_CP=
set ARGS=
:parse
if "%~1"=="" goto run
if "%~1"=="-cp" (
    set EXTRA_CP=%~2
    set ARGS=%ARGS% -cp %~2
    shift
    shift
    goto parse
)
if "%~1"=="--classpath" (
    set EXTRA_CP=%~2
    set ARGS=%ARGS% --classpath %~2
    shift
    shift
    goto parse
)
set ARGS=%ARGS% %1
shift
goto parse

:run
if defined EXTRA_CP (
    java -cp "%SIYO_JAR%;%EXTRA_CP%" Main %ARGS%
) else (
    java -jar "%SIYO_JAR%" %ARGS%
)
