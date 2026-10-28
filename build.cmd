@echo off
setlocal enabledelayedexpansion
rem eclipse-driver build. Usage: build.cmd [test|package|all|it] [--jdk <dir>] [--target <install>] [--plugins <dir>]
rem JDK: --jdk, else %ECLIPSE_DRIVER_JDK%, else the owner's default. The Java 8 on PATH is never used.

set "VERSION=0.1.0"
set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "GOAL=all"
set "JDK=%ECLIPSE_DRIVER_JDK%"
set "TARGET=A:\sandbox\Products\ISOLAR-AB\ISOLAR-AB_V12.13.0RC1"
set "LAUNCHER=ISOLAR-AB.exe"
set "PLUGINS=A:\sandbox\workspace\Orion_WS\Target\Target_12.14.0_Oct26\ecl_target\4.36_with_Artop_4.19\eclipse\plugins"
set "AGENT=A:\sandbox\workspace\Orion_WS\WS\12.14.0_Oct26\com.bosch.eclipsedriver.agent"

:args
if "%~1"=="" goto argsdone
if /i "%~1"=="--jdk" (set "JDK=%~2" & shift & shift & goto args)
if /i "%~1"=="--target" (set "TARGET=%~2" & shift & shift & goto args)
if /i "%~1"=="--launcher" (set "LAUNCHER=%~2" & shift & shift & goto args)
if /i "%~1"=="--plugins" (set "PLUGINS=%~2" & shift & shift & goto args)
set "GOAL=%~1"
shift
goto args
:argsdone

if "%JDK%"=="" set "JDK=C:\toolbase\_ldata\java_jdk\.98-21.0.7.6"
if not exist "%JDK%\bin\javac.exe" (echo ERROR: no javac in "%JDK%". Use --jdk or set ECLIPSE_DRIVER_JDK. & exit /b 2)
set "JAVAC=%JDK%\bin\javac.exe"
set "JAVA=%JDK%\bin\java.exe"
set "JAR=%JDK%\bin\jar.exe"
set "BUILD=%ROOT%\build"
set "DIST=%ROOT%\dist"
set "FATJAR=%DIST%\eclipse-driver-server.jar"
set "TESTARGS="

if /i "%GOAL%"=="test" (call :test & exit /b !ERRORLEVEL!)
if /i "%GOAL%"=="it" goto doit
if /i "%GOAL%"=="package" goto doall
if /i "%GOAL%"=="all" goto doall
echo ERROR: unknown goal "%GOAL%"
exit /b 2

:doit
call :package || exit /b 1
if exist "%BUILD%\it" rmdir /s /q "%BUILD%\it"
mkdir "%BUILD%\it"
"%JAVAC%" --release 21 -encoding UTF-8 -cp "%BUILD%\server-classes" -d "%BUILD%\it" "%ROOT%\it\src\LaunchIT.java" || exit /b 1
set "WS=%TEMP%\eclipse-driver-it-workspace"
"%JAVA%" -cp "%BUILD%\server-classes;%BUILD%\it" LaunchIT "%JAVA%" "%FATJAR%" "%TARGET%\%LAUNCHER%" "%WS%"
exit /b !ERRORLEVEL!

:doall
call :package || exit /b 1
set "TESTARGS=-Ded.fatjar=%FATJAR%"
call :test
exit /b !ERRORLEVEL!

:test
if exist "%BUILD%\test" rmdir /s /q "%BUILD%\test"
mkdir "%BUILD%\test"
rem Unit tests compile the plain-JDK sources only: protocol, server, agent core, tests.
dir /s /b "%ROOT%\protocol\src\*.java" "%ROOT%\server\src\*.java" "%ROOT%\test\*.java" > "%BUILD%\test-sources.txt" 2>nul
dir /s /b "%AGENT%\src\com\bosch\eclipsedriver\agent\core\*.java" >> "%BUILD%\test-sources.txt" 2>nul
"%JAVAC%" --release 21 -encoding UTF-8 -d "%BUILD%\test" @"%BUILD%\test-sources.txt" || exit /b 1
"%JAVA%" %TESTARGS% -cp "%BUILD%\test" edtest.TestRunner "%BUILD%\test"
exit /b %ERRORLEVEL%

:package
echo == package %VERSION%
if exist "%BUILD%\agent-classes" rmdir /s /q "%BUILD%\agent-classes"
if exist "%BUILD%\server-classes" rmdir /s /q "%BUILD%\server-classes"
if exist "%BUILD%\fat" rmdir /s /q "%BUILD%\fat"
mkdir "%BUILD%\agent-classes" "%BUILD%\server-classes" "%BUILD%\fat\embedded"
if not exist "%DIST%" mkdir "%DIST%"
rem Agent bundle: protocol + agent sources, compiled against the target's plugins.
dir /s /b "%ROOT%\protocol\src\*.java" "%AGENT%\src\*.java" > "%BUILD%\agent-sources.txt"
"%JAVAC%" --release 21 -encoding UTF-8 -cp "%PLUGINS%\*" -implicit:none -d "%BUILD%\agent-classes" @"%BUILD%\agent-sources.txt" || exit /b 1
"%JAR%" --create --file "%BUILD%\fat\embedded\agent-bundle.jar" --manifest "%AGENT%\META-INF\MANIFEST.MF" -C "%BUILD%\agent-classes" . || exit /b 1
rem Attach stub: plain JDK, older release so it loads in the target JVM.
if exist "%BUILD%\stub-classes" rmdir /s /q "%BUILD%\stub-classes"
mkdir "%BUILD%\stub-classes"
dir /s /b "%ROOT%\stub\src\*.java" > "%BUILD%\stub-sources.txt"
"%JAVAC%" --release 17 -encoding UTF-8 -d "%BUILD%\stub-classes" @"%BUILD%\stub-sources.txt" || exit /b 1
"%JAR%" --create --file "%BUILD%\fat\embedded\attach-stub.jar" --manifest "%ROOT%\stub\MANIFEST.MF" -C "%BUILD%\stub-classes" . || exit /b 1
rem Server: protocol + server sources, no dependencies.
dir /s /b "%ROOT%\protocol\src\*.java" "%ROOT%\server\src\*.java" > "%BUILD%\server-sources.txt"
"%JAVAC%" --release 21 -encoding UTF-8 -d "%BUILD%\server-classes" @"%BUILD%\server-sources.txt" || exit /b 1
"%JAVA%" "%ROOT%\tools\Pack.java" %VERSION% "%BUILD%\fat\embedded" "%BUILD%\fat\embedded\agent-bundle.jar" "%BUILD%\fat\embedded\attach-stub.jar" || exit /b 1
"%JAR%" --create --file "%FATJAR%" --main-class com.bosch.eclipsedriver.server.Main -C "%BUILD%\server-classes" . -C "%BUILD%\fat" embedded || exit /b 1
echo built %FATJAR%
"%JAVA%" -cp "%BUILD%\server-classes" "%ROOT%\tools\GenRef.java" "%ROOT%\skill\eclipse-driver\reference" || exit /b 1
rem Portable wrapper (uses JAVA_HOME or PATH, not this machine's JDK), plus the skill and docs so dist\ can be shared as is.
copy /y "%ROOT%\tools\eclipse-driver-server.cmd" "%DIST%\eclipse-driver-server.cmd" >nul || exit /b 1
copy /y "%ROOT%\README.md" "%DIST%\README.md" >nul
rem dist\ is also a Claude/Copilot plugin root: .claude-plugin\plugin.json, .mcp.json and skills\ next to the server.
xcopy /e /i /y /q "%ROOT%\plugin" "%DIST%" >nul || exit /b 1
xcopy /e /i /y /q "%ROOT%\skill\eclipse-driver" "%DIST%\skills\eclipse-driver" >nul || exit /b 1
if exist "%DIST%\skill" rmdir /s /q "%DIST%\skill"
exit /b 0
