# =====================================================================
# tunnel.ps1 - 24-hour public link watchdog (Cloudflare Quick Tunnel)
# Keeps the Spring Boot app AND the public tunnel alive; regenerates
# public-link.txt whenever the tunnel URL changes. No account/token
# required. Run:  powershell -ExecutionPolicy Bypass -File tunnel.ps1
# =====================================================================
$root     = $PSScriptRoot
$appDir   = Join-Path $root 'my-first-spring-api'
$cf       = Join-Path $root 'tools\cloudflared.exe'
$linkFile = Join-Path $root 'public-link.txt'
$tlog     = Join-Path $root 'tunnel-cf.log'

function Test-App {
    try {
        $r = Invoke-WebRequest 'http://localhost:8081/api/kitchens' -UseBasicParsing -TimeoutSec 5
        return ($r.StatusCode -eq 200)
    } catch { return $false }
}

function Start-App {
    Write-Output ("[{0}] starting Spring Boot app on :8081 ..." -f (Get-Date -Format 'HH:mm:ss'))
    Start-Process -FilePath 'cmd.exe' `
        -ArgumentList "/c cd /d `"$appDir`" && mvnw.cmd spring-boot:run > `"$root\app-run.log`" 2>&1" `
        -WindowStyle Hidden
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 5
        if (Test-App) {
            Write-Output ("[{0}] app is UP" -f (Get-Date -Format 'HH:mm:ss'))
            return $true
        }
    }
    Write-Output "app failed to come up"
    return $false
}

function Start-Tunnel {
    if (Test-Path $tlog) { Remove-Item $tlog -Force -ErrorAction SilentlyContinue }
    Write-Output ("[{0}] starting cloudflared quick tunnel ..." -f (Get-Date -Format 'HH:mm:ss'))
    Start-Process -FilePath $cf `
        -ArgumentList "tunnel --url http://localhost:8081 --protocol http2 --logfile `"$tlog`" --loglevel info" `
        -WindowStyle Hidden
    for ($i = 0; $i -lt 36; $i++) {
        Start-Sleep -Seconds 5
        if (Test-Path $tlog) {
            $m = Select-String -Path $tlog -Pattern 'https://[a-zA-Z0-9-]+\.trycloudflare\.com' | Select-Object -First 1
            if ($m) { return $m.Matches[0].Value }
        }
    }
    return $null
}

function Write-LinkFile($url) {
    # Defensive: keep only the bare https URL, whatever the caller passed in
    if ($url -match 'https://[a-zA-Z0-9-]+\.trycloudflare\.com') {
        $url = $Matches[0]
    } else {
        Write-Output ("refusing to write link file - not a valid tunnel URL: " + $url)
        return
    }
    $lines = @(
        "PUBLIC WEB APP (seller dashboard) : $url/seller.html",
        "PUBLIC WEB APP (buyer view)       : $url/",
        "PUBLIC API BASE                   : $url/api",
        "Health probe                      : $url/api/kitchens",
        "Generated (local)                 : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    )
    Set-Content -Path $linkFile -Value $lines -Encoding UTF8
    Write-Output ($lines -join "`n")
}

# ---------------- main watchdog loop (runs for 24h+) ----------------
Write-Output "=== 24h public-link watchdog started ==="
while ($true) {
    if (-not (Test-App)) { [void](Start-App) }

    # Adopt an already-running healthy tunnel instead of recreating it:
    # quick-tunnel URLs change on every recreate, so reuse whenever possible.
    $url = $null
    if (Get-Process cloudflared -ErrorAction SilentlyContinue) {
        if (Test-Path $tlog) {
            $m = Select-String -Path $tlog -Pattern 'https://[a-zA-Z0-9-]+\.trycloudflare\.com' |
                 Select-Object -Last 1
            if ($m) {
                $candidate = $m.Matches[0].Value
                try {
                    $null = Invoke-WebRequest ($candidate + '/api/kitchens') -UseBasicParsing -TimeoutSec 15
                    $url = $candidate
                    Write-Output ("[{0}] adopted existing healthy tunnel" -f (Get-Date -Format 'HH:mm:ss'))
                } catch { $url = $null }
            }
        }
    }

    if (-not $url) { $url = Start-Tunnel }

    if ($url) {
        Write-LinkFile $url
        # inner supervision loop: keep this tunnel alive while it responds
        # (tolerant: 3 consecutive failures before recreating - quick-tunnel
        #  URLs change on every recreate, so avoid churn on transient blips)
        while ($true) {
            Start-Sleep -Seconds 60
            if (-not (Test-App)) { [void](Start-App) }
            $failures = 0
            $healthy = $false
            for ($try = 1; $try -le 3; $try++) {
                try {
                    $null = Invoke-WebRequest ($url + '/api/kitchens') -UseBasicParsing -TimeoutSec 20
                    $healthy = $true
                    break
                } catch {
                    $failures++
                    Start-Sleep -Seconds 15
                }
            }
            if (-not $healthy) {
                Write-Output ("[{0}] tunnel unhealthy after 3 probes - recreating ..." -f (Get-Date -Format 'HH:mm:ss'))
                Get-Process cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force
                break
            }
        }
    } else {
        Write-Output "tunnel could not start - retrying in 30s"
        Get-Process cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force
        Start-Sleep -Seconds 30
    }
}
