@echo off
setlocal
rem ============================================================================
rem  SocioMart - one-command runner   (Windows Command Prompt)
rem
rem    start.cmd
rem
rem  This single command verifies JDK 21+, applies optional `.env` overrides,
rem  and boots Spring Boot - which creates the DB schema, seeds the demo
rem  marketplace, and serves both the REST API and the SocioMart SPA. Missing
rem  Maven/dependencies are downloaded automatically by the Maven wrapper.
rem ============================================================================
cd /d "%~dp0"

rem ---- 1. Optional .env overrides (falls back to defaults) -------------------
if exist ".env" (
    for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do (
        if not "%%a"=="" set "%%a=%%b"
    )
)
if not defined PORT set PORT=8081

rem ---- 2. Locate Java (JAVA_HOME wins, otherwise PATH) ------------------------
set "JAVA_BIN=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"

"%JAVA_BIN%" -version >nul 2>&1
if errorlevel 1 (
    echo.
    echo   ERROR: Java was not found.
    echo   Install JDK 21 (Temurin: https://adoptium.net), then set JAVA_HOME or add java to PATH.
    echo.
    exit /b 1
)

set "JAVA_VER="
for /f "tokens=3" %%v in ('"%JAVA_BIN%" -version 2^>^&1') do set "JAVA_VER=%%~v"
set "JAVA_MAJOR="
for /f "tokens=1 delims=." %%m in ("%JAVA_VER%") do set "JAVA_MAJOR=%%m"

if "%JAVA_MAJOR%" LSS "21" (
    echo.
    echo   ERROR: JDK 21 or newer is required, but Java %JAVA_VER% was found.
    echo   Install Temurin 21 from https://adoptium.net and rerun start.cmd
    echo.
    exit /b 1
)
echo   [setup] Java OK: %JAVA_VER%   ^(PORT=%PORT%^)

rem ---- 3. Boot ------------------------------------------------------------------
cd my-first-spring-api
call .\mvnw.cmd spring-boot:run
exit /b %errorlevel%