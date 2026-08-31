$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8081'
$pass = 0; $fail = 0

function Check($name, $ok, $detail) {
  $result = & $ok
  if ($result) { $script:pass++; Write-Host ("PASS  " + $name + "  -> " + $detail) }
  else { $script:fail++; Write-Host ("FAIL  " + $name + "  -> " + $detail) }
}

function NewSession() { New-Object Microsoft.PowerShell.Commands.WebRequestSession }

function Req($method, $url, $body, $session) {
  $args = @{ Uri = ($base + $url); Method = $method; WebSession = $session; UseBasicParsing = $true; TimeoutSec = 20 }
  if ($null -ne $body -and $method -ne 'GET') { $args['ContentType'] = 'application/json'; $args['Body'] = ($body | ConvertTo-Json -Depth 8 -Compress) }
  try { $r = Invoke-WebRequest @args; return @{ status = [int]$r.StatusCode; data = ($r.Content | ConvertFrom-Json) } }
  catch { $resp = $_.Exception.Response; $code = if ($resp) { [int]$resp.StatusCode } else { 0 }; $b = $null; if ($resp -and $resp.GetResponseStream()) { $b = (New-Object System.IO.StreamReader($resp.GetResponseStream())).ReadToEnd() | ConvertFrom-Json -ErrorAction SilentlyContinue }; return @{ status = $code; data = $b; error = $_.Exception.Message } }
}

function OtpLogin($session, $mobile, $name, $flat) {
  $r1 = Req 'POST' '/api/auth/otp/request' @{ mobileNumber = $mobile } $session
  if ($r1.status -ne 200 -or -not $r1.data.otp) { return $r1 }
  return Req 'POST' '/api/auth/otp/verify' @{ mobileNumber = $mobile; otpCode = $r1.data.otp; name = $name; flatHouseNumber = $flat } $session
}

Write-Host '=== Empty marketplace check ==='
$empty = Req 'GET' '/api/marketplace' @{} (NewSession)
Check 'marketplace empty on fresh start' { $empty.status -eq 200 -and @($empty.data.kitchens).Count -eq 0 } ("kitchens=" + @($empty.data.kitchens).Count)

Write-Host ''
Write-Host '=== SELLER: login, become seller, create kitchen, add offering ==='
$sellerS = NewSession
$sl = OtpLogin $sellerS '9876500001' 'Ramesh Cook' 'A-1102'
Check 'seller OTP login' { $sl.status -eq 200 -and $sl.data.authenticated } ("role=" + $sl.data.role)
$bs = Req 'POST' '/api/auth/become-seller' @{} $sellerS
Check 'become seller' { $bs.status -eq 200 -and $bs.data.role -eq 'SELLER' } ("role=" + $bs.data.role)
$slug = 'ramesh-kitchen-' + (Get-Random -Maximum 99999)
$ck = Req 'POST' '/api/seller/kitchen' @{
  name = $slug; displayName = 'Ramesh Homely Kitchen'; shortDescription = 'Fresh homely thalis'
  society = 'Pride World City'; building = 'Tower A'; upiId = 'ramesh@upi'; availableToday = $true
} $sellerS
Check 'create kitchen' { $ck.status -eq 200 -and $ck.data.id } ("kitchenId=" + $ck.data.id + " slug=" + $slug)
$kId = $ck.data.id
$cp = Req 'POST' ("/api/seller/products?kitchenId=" + $kId) @{
  name = 'Dal Tadka'; description = 'Yellow dal with tadka, served with rice'; price = 120
  priceUnit = 'per plate'; availableToday = $true; maxQuantity = 10
} $sellerS
Check 'add offering' { $cp.status -eq 200 -and $cp.data.id -and $cp.data.remainingQuantity -eq 10 } ("productId=" + $cp.data.id + " stock=" + $cp.data.remainingQuantity)
$productId = $cp.data.id

Write-Host ''
Write-Host '=== BUYER: login and see the new kitchen on marketplace ==='
$buyerS = NewSession
$bl = OtpLogin $buyerS '9876500002' 'Sunita Gupta' 'C-2210'
Check 'buyer OTP login' { $bl.status -eq 200 -and $bl.data.authenticated } ("role=" + $bl.data.role)
$mk = Req 'GET' '/api/marketplace' @{} $buyerS
Check 'marketplace shows new kitchen' { $mk.status -eq 200 -and @($mk.data.kitchens).Count -eq 1 -and @($mk.data.availableToday).Count -eq 1 } ("kitchens=" + @($mk.data.kitchens).Count + " items=" + @($mk.data.availableToday).Count)
$kdt = Req 'GET' ('/api/kitchens/' + $slug) @{} $buyerS
Check 'kitchen detail page' { $kdt.status -eq 200 -and $kdt.data.kitchen.displayName -eq 'Ramesh Homely Kitchen' } ($kdt.data.kitchen.displayName)

Write-Host ''
Write-Host '=== BUYER: cart, place order, pay, rate ==='
$d1 = Req 'POST' ("/api/buyer/orders/draft?kitchenId=" + $kId) @(@{ productId = $productId; quantity = 2 }) $buyerS
Check 'draft add 2x' { $d1.status -eq 200 -and $d1.data.items.Count -eq 1 -and [int]$d1.data.items[0].quantity -eq 2 } ("status=" + $d1.status + " total=" + $d1.data.totalAmount)
$poor = Req 'POST' '/api/buyer/orders/place' @{
  paymentStatus = 'PENDING'
  buyerDetails = @{ name = 'Sunita Gupta'; mobileNumber = '9876500002'; society = 'Pride World City'; building = 'Tower C'; flatHouseNumber = 'C-2210' }
  customInstructions = 'No coriander please'
} $buyerS
Check 'place order' { $poor.status -eq 200 -and $poor.data.id } ("order=" + $poor.data.orderNumber + " total=" + $poor.data.totalAmount)
$orderId = $poor.data.id
$pay = Req 'PATCH' ("/api/buyer/orders/" + $orderId + "/payment-status") @{ paymentStatus = 'PAID' } $buyerS
Check 'mark paid' { $pay.status -eq 200 -and $pay.data.paymentStatus -eq 'PAID' } ($pay.data.paymentStatus)
$og = Req 'GET' ("/api/buyer/orders/" + $orderId) $null $buyerS
Check 'order detail shows buyer + kitchen + items' { $og.status -eq 200 -and $og.data.buyer.name -eq 'Sunita Gupta' -and $og.data.kitchen -and @($og.data.items).Count -eq 1 } ("buyer=" + $og.data.buyer.name)
Write-Host ''
Write-Host '=== SELLER: sees the order, stock decremented ==='
$buyerS2 = NewSession
$bl2 = OtpLogin $buyerS2 '9876500002' 'Sunita Gupta' 'C-2210'
$mk2 = Req 'GET' '/api/marketplace' @{} $buyerS2
$prodObj = $mk2.data.availableToday | Where-Object { $_.id -eq $productId } | Select-Object -First 1
Check 'stock consumed after order' { ([double]$prodObj.remainingQuantity) -eq 8 } ("stock=" + $prodObj.remainingQuantity)
$so = Req 'GET' '/api/seller/orders' @{} $sellerS
if ($so.data -and @($so.data).Count -ge 1) { $soBuyer = $so.data[0].buyer.name } else { $soBuyer = 'null' }
Check 'seller sees order' { $so.status -eq 200 -and $null -ne $so.data -and @($so.data).Count -ge 1 -and $so.data[0].buyer.name -eq 'Sunita Gupta' } ("orders=" + @($so.data).Count + " buyer=" + $soBuyer)
$adv = Req 'PATCH' ("/api/seller/orders/" + $orderId + "/status") @{ orderStatus = 'DELIVERED' } $sellerS
Check 'seller advance to DELIVERED' { $adv.status -eq 200 -and $adv.data.orderStatus -eq 'DELIVERED' } ($adv.data.orderStatus)
$adv2 = Req 'PATCH' ("/api/seller/orders/" + $orderId + "/status") @{ orderStatus = 'COMPLETED' } $sellerS
Check 'seller advance to COMPLETED' { $adv2.status -eq 200 -and $adv2.data.orderStatus -eq 'COMPLETED' } ($adv2.data.orderStatus)
$adv3 = Req 'PATCH' ("/api/seller/orders/" + $orderId + "/status") @{ orderStatus = 'CONFIRMED' } $sellerS
Check 'blocked after COMPLETED' { $adv3.status -eq 400 } ("status=" + $adv3.status)
$rate = Req 'POST' ("/api/buyer/orders/" + $orderId + "/rate?rating=5") $null $buyerS
Check 'rate order 5' { $rate.status -eq 200 -and $rate.data.orderStatus -eq 'COMPLETED' } ($rate.data.orderStatus)
$rateDup = Req 'POST' ("/api/buyer/orders/" + $orderId + "/rate?rating=4") $null $buyerS
Check 'rate blocked after completion' { $rateDup.status -eq 400 } ("status=" + $rateDup.status)

Write-Host ''
Write-Host '=== SELLER: kitchen settings update + offering pause/delete ==='
$ku = Req 'PUT' ("/api/seller/kitchen/" + $kId) @{ displayName = 'Ramesh Premium Kitchen'; shortDescription = 'Now with premium dals'; building = 'Tower B' } $sellerS
Check 'update kitchen settings' { $ku.status -eq 200 -and $ku.data.displayName -eq 'Ramesh Premium Kitchen' } ($ku.data.displayName)
$pu = Req 'PUT' ("/api/seller/products/" + $productId) @{ name = 'Dal Tadka'; price = 135; availableToday = $false; maxQuantity = 10 } $sellerS
Check 'pause offering' { $pu.status -eq 200 -and $pu.data.availableToday -eq $false } ("availableToday=" + $pu.data.availableToday)
$del = Req 'DELETE' ("/api/seller/products/" + $productId) $null $sellerS
Check 'delete offering' { $del.status -eq 200 -or $del.status -eq 204 } ($del.status)

Write-Host ''
Write-Host '=== PROFILE update ==='
$pfu = Req 'PUT' '/api/buyer/profile' @{ name = 'Sunita Gupta Sharma'; mobileNumber = '9876500002'; society = 'Pride World City'; building = 'Tower D'; flatHouseNumber = 'D-99' } $buyerS
Check 'buyer profile update' { $pfu.status -eq 200 -and $pfu.data.building -eq 'Tower D' } ("building=" + $pfu.data.building)

Write-Host ''
Write-Host ("RESULT: " + $pass + " passed, " + $fail + " failed")
if ($fail -gt 0) { exit 1 } else { exit 0 }