$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8081'
$sess = New-Object Microsoft.PowerShell.Commands.WebRequestSession
function J($method, $uri, $body) {
  $json = if ($body) { $body | ConvertTo-Json -Depth 6 } else { $body }
  try {
    $r = Invoke-WebRequest -Uri "$base$uri" -Method $method -Body $json -ContentType 'application/json' -WebSession $sess -UseBasicParsing -TimeoutSec 15
    if ($r.Content) { $r.Content | ConvertFrom-Json } else { $null }
  } catch {
    $resp = $_.Exception.Response
    $code = if ($resp) { [int]$resp.StatusCode } else { 'ERR' }
    Write-Host "  !! $method $uri -> $code"
    throw
  }
}
$script:pass = 0; $script:fail = 0
function Check($name, $cond) {
  if ($cond) { $script:pass++; Write-Host "PASS  $name" }
  else { $script:fail++; Write-Host "FAIL  $name" }
}

# --- static assets ---
$idx = Invoke-WebRequest -Uri "$base/" -WebSession $sess -UseBasicParsing
Check 'GET / (index page)' ($idx.StatusCode -eq 200 -and $idx.Content -like '*SocioMart*')
$css = Invoke-WebRequest -Uri "$base/css/styles.css" -WebSession $sess -UseBasicParsing
Check 'GET /css/styles.css' ($css.StatusCode -eq 200)
$jsf = Invoke-WebRequest -Uri "$base/js/app.js" -WebSession $sess -UseBasicParsing
Check 'GET /js/app.js complete' ($jsf.StatusCode -eq 200 -and $jsf.Content.Length -gt 70000)

# --- marketplace ---
$m = J GET '/api/marketplace'
Check 'marketplace: kitchens >= 2' ($m.kitchens.Count -ge 2)
Check 'marketplace: availableToday >= 4' ($m.availableToday.Count -ge 4)
Check 'marketplace: popularProducts present' ($null -ne $m.popularProducts)
Check 'marketplace: newProducts present' ($null -ne $m.newProducts)

# --- kitchen detail ---
$kd = J GET '/api/kitchens/aartiskitchen'
Check 'kitchen detail: kitchen obj' ($null -ne $kd.kitchen)
Check 'kitchen detail: products >= 3' ($kd.products.Count -ge 3)
Check 'kitchen detail: upiId set' ($kd.kitchen.upiId -like '*@upi')

# --- search ---
$sr = J GET '/api/search?q=dosa'
Check 'search: dosa found' ($sr.products.Count -ge 1)
$sr2 = J GET '/api/search?q=aarti'
Check 'search: aarti kitchen found' ($sr2.kitchens.Count -ge 1)

# --- OTP auth flow ---
$otp = J POST '/api/auth/otp/request' @{ mobileNumber = '9876543210' }
Check 'otp request: 4-digit otp returned' ($otp.otp -match '^\d{4}$')
$me1 = J GET '/api/auth/me'
Check 'me: unauthenticated before verify' ($me1.authenticated -eq $false)
$v = J POST '/api/auth/otp/verify' @{ mobileNumber = '9876543210'; otpCode = $otp.otp; name = 'Test Buyer'; flatHouseNumber = 'B-402' }
Check 'otp verify: authenticated' ($v.authenticated -eq $true)
$me2 = J GET '/api/auth/me'
Check 'me: session persists' ($me2.authenticated -eq $true -and $me2.name -eq 'Test Buyer')

# --- profile ---
$pf = J PUT '/api/buyer/profile' @{ name = 'Test Buyer'; society = 'Pride World City'; building = 'Tower B'; flatHouseNumber = 'B-402' }
Check 'profile: building saved' ($pf.building -eq 'Tower B')
