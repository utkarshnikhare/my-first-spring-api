# =============================================================================
#  SocioMart - one-command runner   (Windows PowerShell)
#
#    .\start.ps1
#    (if the Execution Policy blocks it:  powershell -ExecutionPolicy Bypass -File .\start.ps1)
#
#  Verifies JDK 21+, applies optional `.env` overrides, and boots Spring Boot,
#  which creates the DB schema, seeds the demo marketplace, and serves both the
#  REST API and the SocioMart SPA. Maven/dependencies download on first run.
# =============================================================================
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $MyInvocation.MyCommand.Path)

# ---- 1. Optional .env overrides (falls back to defaults) ----------------------
if (Test-Path '.env') {
    Get-Content '.env' | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
            [Environment]::SetEnvironmentVariable($Matches[1].Trim(), $Matches[2].Trim(), 'Process')
        }
    }
}
if (-not $env:PORT) { $env:PORT = '8081' }

# ---- 2. Locate Java (JAVA_HOME wins, otherwise PATH) ---------------------------
$javaBin = 'java'
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $javaBin = Join-Path $env:JAVA_HOME 'bin\java.exe'
}
try {
    $verLine = (& $javaBin -version 2>&1 | Select-Object -First 1)
} catch {
    Write-Host ''
    Write-Host '  ERROR: Java was not found.' -ForegroundColor Red
    Write-Host '  Install JDK 21 (Temurin: https://adoptium.net), then rerun start.ps1'
    Write-Host ''
    exit 1
}
$major = 0
if ($verLine -match 'version "?([0-9]+)') { $major = [int]$Matches[1] }
if ($major -lt 21) {
    Write-Host ''
    Write-Host ("  ERROR: JDK 21 or newer is required, but Java {0} was found." -f $verLine) -ForegroundColor Red
    Write-Host '  Install Temurin 21 from https://adoptium.net and rerun start.ps1'
    Write-Host ''
    exit 1
}
Write-Host ("  [setup] Java OK: {0}   (PORT={1})" -f $verLine.Trim(), $env:PORT)

# ---- 3. Boot --------------------------------------------------------------------
Set-Location my-first-spring-api
& .\mvnw.cmd spring-boot:run
exit $LASTEXITCODE