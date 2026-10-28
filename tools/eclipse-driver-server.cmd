@echo off
rem eclipse-driver MCP server launcher. Needs Java 21 or newer.
rem Java is taken from ECLIPSE_DRIVER_JAVA_HOME, then JAVA_HOME, then PATH.
setlocal
set "JAVA_EXE=java"
if defined ECLIPSE_DRIVER_JAVA_HOME (
    set "JAVA_EXE=%ECLIPSE_DRIVER_JAVA_HOME%\bin\java.exe"
) else if defined JAVA_HOME (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)

set "JV="
for /f "tokens=3" %%v in ('"%JAVA_EXE%" -version 2^>^&1') do if not defined JV set "JV=%%~v"
if not defined JV (
    >&2 echo eclipse-driver: could not run "%JAVA_EXE%". Install Java 21+ and set JAVA_HOME or ECLIPSE_DRIVER_JAVA_HOME.
    exit /b 1
)
set "MAJOR="
for /f "delims=." %%m in ("%JV%") do set "MAJOR=%%m"
if "%MAJOR%"=="1" set "MAJOR=8"
if %MAJOR% LSS 21 (
    >&2 echo eclipse-driver: Java %JV% found at "%JAVA_EXE%", but Java 21 or newer is required. Set JAVA_HOME or ECLIPSE_DRIVER_JAVA_HOME to a JDK 21+.
    exit /b 1
)

"%JAVA_EXE%" -jar "%~dp0eclipse-driver-server.jar" %*
