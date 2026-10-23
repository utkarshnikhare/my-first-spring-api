# SocioMart Seller App — comprehensive E2E deep-test suite
param([string]$TargetBase = 'http://localhost:8081')
$ErrorActionPreference = 'Stop'
$Base = $TargetBase
$script:pass = 0
$script:fail = 0
function Check($name, $cond, $detail) {
  if ($cond) { $script:pass++; Write-Output ("PASS | " + $name) }
  else { $script:fail++; Write-Output ("FAIL | " + $name + " | " + $detail) }
}
function New-Sess { New-Object Microsoft.PowerShell.Commands.WebRequestSession }
function Api($sess, $method, $uri, $body) {
  try {
    $p = @{ Uri = ($Base + $uri); Method = $method; WebSession = $sess; UseBasicParsing = $true; ContentType = 'application/json' }
    if ($null -ne $body) { $p.Body = (ConvertTo-Json -InputObject $body -Depth 8) }
    $r = Invoke-WebRequest @p
    $obj = $null
    if ($r.Content) { try { $obj = $r.Content | ConvertFrom-Json } catch { $obj = $r.Content } }
    return @{ Status = [int]$r.StatusCode; Body = $obj }
  } catch {
    $resp = $_.Exception.Response
    if ($null -ne $resp) {
      $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
      $txt = $sr.ReadToEnd()
      $obj = $null
      try { $obj = $txt | ConvertFrom-Json } catch { $obj = @{ raw = $txt } }
      return @{ Status = [int]$resp.StatusCode; Body = $obj }
    }
    throw
  }
}
function Login($sess, $mobile, $name, $flat) {
  # OTP was removed (client demo): /api/auth/demo-login authenticates by mobile only.
  # Existing seeded sellers (e.g. Aarti 9100000001) resolve to their SELLER role.
  $vb = @{ mobileNumber = $mobile }
  if ($name) { $vb.name = $name; $vb.flatHouseNumber = $flat }
  return Api $sess 'POST' '/api/auth/demo-login' $vb
}
$today = (Get-Date).ToString('yyyy-MM-dd')

# ===== A) Seller Aarti login (session cookie) =====
$aarti = New-Sess
$r = Login $aarti '9100000001' $null $null
Check 'A1 seller login Aarti authenticated' ($r.Status -eq 200 -and $r.Body.authenticated -eq $true) ("status=" + $r.Status)

# ===== B) Public menu views -> MENU_VIEW events (kitchen-scoped) =====
$anon = New-Sess
$null = Api $anon 'GET' '/api/kitchens/aarti-kitchen' $null
$null = Api $anon 'GET' '/api/kitchens/aarti-kitchen' $null

# ===== C) Dashboard: REAL views metric (was hardcoded 37 + orders*2) =====
$dash = Api $aarti 'GET' '/api/seller-app/dashboard' $null
$offerings = @($dash.Body.offerings)
$fakeVal = 37 + $dash.Body.totalOrders * 2
Check 'C1 dashboard returns 200' ($dash.Status -eq 200) ("status=" + $dash.Status)
Check 'C2 viewsToday is real MENU_VIEW count (>=2, not fake formula)' ($dash.Body.viewsToday -ge 2 -and $dash.Body.viewsToday -ne $fakeVal) ("viewsToday=" + $dash.Body.viewsToday + " fake=" + $fakeVal)

# ===== D) Inventory control: +/- deltas, negative overflow =====
$poha = $offerings | Where-Object { $_.name -eq 'Poha' } | Select-Object -First 1
Check 'D0 Poha offering present with maxQuantity' (($null -ne $poha) -and ($null -ne $poha.maxQuantity)) ''
$orig = [int]$poha.remainingQuantity
$r = Api $aarti 'PATCH' ("/api/seller-app/products/" + $poha.id + "/inventory") @{ delta = 1 }
Check 'D1 inventory +1 reflected' ($r.Status -eq 200 -and [int]$r.Body.remainingQuantity -eq ($orig + 1)) ("status=" + $r.Status + " rem=" + $r.Body.remainingQuantity)
$r = Api $aarti 'PATCH' ("/api/seller-app/products/" + $poha.id + "/inventory") @{ delta = -1 }
Check 'D2 inventory -1 restores original' ($r.Status -eq 200 -and [int]$r.Body.remainingQuantity -eq $orig) ("rem=" + $r.Body.remainingQuantity)
$r = Api $aarti 'PATCH' ("/api/seller-app/products/" + $poha.id + "/inventory") @{ delta = -999 }
Check 'D3 negative overflow rejected 400' ($r.Status -eq 400) ("status=" + $r.Status)


# ===== E) Cross-seller authorization =====
$meena = New-Sess
$null = Login $meena '9100000002' $null $null
$r = Api $meena 'PATCH' ("/api/seller-app/products/" + $poha.id + "/inventory") @{ delta = 1 }
Check 'E1 other seller inventory update -> 403 FORBIDDEN' ($r.Status -eq 403) ("status=" + $r.Status)

# ===== F) Favourites cap: strict 3 templates per seller =====
# Pre-clean: delete any pre-existing templates so the cap test is deterministic regardless of seed state
$existing = Api $aarti 'GET' '/api/seller-app/templates' $null
foreach ($t in @($existing.Body)) { $null = Api $aarti 'DELETE' ("/api/seller-app/templates/" + $t.id) $null }
$t1 = Api $aarti 'POST' '/api/seller-app/templates' @{ name = 'E2E Unlimited Special'; description = 'no qty cap'; price = 90; priceUnit = 'plate' }
$t2 = Api $aarti 'POST' '/api/seller-app/templates' @{ name = 'E2E T2'; description = 'x'; price = 50; priceUnit = 'plate' }
$t3 = Api $aarti 'POST' '/api/seller-app/templates' @{ name = 'E2E T3'; description = 'x'; price = 60; priceUnit = 'plate' }
Check 'F1 three templates saved' (($t1.Status -eq 200) -and ($t2.Status -eq 200) -and ($t3.Status -eq 200)) ''
$t4 = Api $aarti 'POST' '/api/seller-app/templates' @{ name = 'E2E T4'; description = 'x'; price = 70; priceUnit = 'plate' }
Check 'F2 4th template rejected 409 with cap message' (($t4.Status -eq 409) -and ($t4.Body.message -like '*Maximum 3*')) ("status=" + $t4.Status + " body=" + ($t4.Body | ConvertTo-Json -Compress))

# ===== G) Unlimited-inventory edge case via template publish =====
$pub = Api $aarti 'POST' ("/api/seller-app/templates/" + $t1.Body.id + "/publish") @{ availableDate = $today }
Check 'G1 publish unlimited template (null max/remaining)' (($pub.Status -eq 200) -and ($null -eq $pub.Body.maxQuantity) -and ($null -eq $pub.Body.remainingQuantity)) ("max=" + $pub.Body.maxQuantity + " rem=" + $pub.Body.remainingQuantity)
$unl = $pub.Body
$r = Api $aarti 'PATCH' ("/api/seller-app/products/" + $unl.id + "/inventory") @{ delta = 1 }
Check 'G2 stepper on unlimited item -> 400 no quantity limit' (($r.Status -eq 400) -and ($r.Body.message -like '*no quantity limit*')) ("status=" + $r.Status)
$r = Api $aarti 'POST' ("/api/seller-app/products/" + $unl.id + "/sold-out") $null
Check 'G3 manual sold-out toggle works on unlimited item' (($r.Status -eq 200) -and ([int]$r.Body.remainingQuantity -eq 0)) ("rem=" + $r.Body.remainingQuantity)

# ===== H) Quick post parse: extract, never auto-publish =====
$cntBefore = @((Api $aarti 'GET' '/api/seller-app/dashboard' $null).Body.offerings).Count
$msg = 'Homemade Paneer Tikka - only Rs.120 per plate. Max 15 plates today. Order before 6 PM, ready by 7:30 PM'
$pq = Api $aarti 'POST' '/api/seller-app/parse-message' @{ message = $msg }
Check 'H1 parse extracts price; publishable=true (eligible for confirm dialog)' (($pq.Status -eq 200) -and ($pq.Body.publishable -eq $true) -and ($pq.Body.price -eq 120) -and ($null -ne $pq.Body.name)) ("price=" + $pq.Body.price + " name=" + $pq.Body.name + " publishable=" + $pq.Body.publishable)
$cntAfter = @((Api $aarti 'GET' '/api/seller-app/dashboard' $null).Body.offerings).Count
Check 'H2 parse did NOT auto-publish (offerings unchanged)' ($cntAfter -eq $cntBefore) ("before=" + $cntBefore + " after=" + $cntAfter)

# ===== I) Buyer places a real PENDING order (Poha x2 = Rs.80) =====
# Extend Poha's cutoff to 23:59 (partial update) so ordering is testable at any hour of day
$cut = Api $aarti 'PUT' ("/api/seller/products/" + $poha.id) @{ cutoffTime = '23:59' }
Check 'I-1 cutoff extension accepted' ($cut.Status -eq 200) ("status=" + $cut.Status)
Check 'I-1c cutoff persisted as 23:59' (($cut.Status -eq 200) -and ($cut.Body.cutoffTime -eq '23:59')) ("cutoff=" + $cut.Body.cutoffTime)
$cutbad = Api $aarti 'PUT' ("/api/seller/products/" + $poha.id) @{ cutoffTime = '25:99' }
Check 'I-1b invalid cutoffTime rejected 400' ($cutbad.Status -eq 400) ("status=" + $cutbad.Status)
# Restock: seed data ships Poha sold-out (remaining=0); bring it back in stock so the buyer flow can run
$stock = Api $aarti 'PATCH' ("/api/seller-app/products/" + $poha.id + "/inventory") @{ delta = 5 }
Check 'I-2 restock Poha +5 accepted' (($stock.Status -eq 200) -and ([int]$stock.Body.remainingQuantity -ge 5)) ("status=" + $stock.Status + " rem=" + $stock.Body.remainingQuantity)
# State-isolation baseline: this instance may carry orders from earlier runs,
# so ALL order-flow assertions are deltas against this snapshot.
$drillBase = Api $aarti 'GET' ("/api/seller-app/orders/product/" + $poha.id) $null
$basePlates = [int]$drillBase.Body.totalPlates
$basePend = [int]$drillBase.Body.pendingCount
$baseCancel = [int]$drillBase.Body.cancelledCount
$baseRows = @($drillBase.Body.customers).Count
$sumBase = Api $aarti 'GET' '/api/seller-app/orders/summary' $null
$aggBase = @($sumBase.Body.products) | Where-Object { $_.productId -eq $poha.id } | Select-Object -First 1
$aggBasePlates = if ($aggBase) { [int]$aggBase.totalPlates } else { 0 }
$aggBaseRev = if ($aggBase) { [double]$aggBase.revenue } else { 0 }
$baseRev = [double]$sumBase.Body.totalRevenue
$baseCanc = [int]$sumBase.Body.cancelledCount
$baseSumPend = [int]$sumBase.Body.pendingCount
$earnBase = Api $aarti 'GET' '/api/seller-app/earnings' $null
$baseEarnPend = [double]$earnBase.Body.pending
$baseConf = [double]$earnBase.Body.confirmedToday
$buyer = New-Sess
$null = Login $buyer '9100000099' 'E2E Buyer' 'Z-999'
$kitchen = Api $anon 'GET' '/api/kitchens/aarti-kitchen' $null
$kid = $kitchen.Body.kitchen.id
Check 'I0 kitchen id resolved' ($null -ne $kid) ''
$draft = Api $buyer 'POST' ("/api/buyer/orders/draft?kitchenId=" + $kid) @(@{ productId = $poha.id; quantity = 2 })
Check 'I1 draft created with total 80' (($draft.Status -eq 200) -and ([double]$draft.Body.totalAmount -eq 80)) ("status=" + $draft.Status + " total=" + $draft.Body.totalAmount)
$place = Api $buyer 'POST' '/api/buyer/orders/place' @{ paymentStatus = 'PENDING'; buyerDetails = @{ name = 'E2E Buyer'; mobileNumber = '9100000099'; flatHouseNumber = 'Z-999' }; customInstructions = 'less spicy' }
Check 'I2 order placed PENDING' (($place.Status -eq 200) -and ($place.Body.paymentStatus -eq 'PENDING')) ("status=" + $place.Status)
$orderId = $place.Body.id

# ===== J) Summary (7A) + drill-down (7B) consistency, pre-cancel =====
$sum = Api $aarti 'GET' '/api/seller-app/orders/summary' $null
Check 'J1 summary pendingCount delta >= +1' (($sum.Body.totalOrderCount -ge 1) -and ($sum.Body.pendingCount -ge ($baseSumPend + 1))) ("pend=" + $sum.Body.pendingCount + " base=" + $baseSumPend)
$pohaAgg = @($sum.Body.products) | Where-Object { $_.productId -eq $poha.id } | Select-Object -First 1
Check 'J2 Poha aggregate delta: +2 plates, +80 revenue' (($null -ne $pohaAgg) -and ($pohaAgg.totalPlates -eq ($aggBasePlates + 2)) -and ([double]$pohaAgg.revenue -eq ($aggBaseRev + 80))) ("plates=" + $pohaAgg.totalPlates + " rev=" + $pohaAgg.revenue)
$drill = Api $aarti 'GET' ("/api/seller-app/orders/product/" + $poha.id) $null
Check 'J3 drill delta: +2 plates, +1 pending, +1 row' (($drill.Body.totalPlates -eq ($basePlates + 2)) -and ($drill.Body.pendingCount -eq ($basePend + 1)) -and (@($drill.Body.customers).Count -eq ($baseRows + 1))) ("plates=" + $drill.Body.totalPlates + " pend=" + $drill.Body.pendingCount + " rows=" + @($drill.Body.customers).Count)
Check 'J4 drill/summary plates consistent' ($drill.Body.totalPlates -eq $pohaAgg.totalPlates) ''

# ===== K) Earnings isolation (pre-cancel) =====
$earn = Api $aarti 'GET' '/api/seller-app/earnings' $null
Check 'K1 pending earnings delta +80 (PENDING only)' ([double]$earn.Body.pending -eq ($baseEarnPend + 80)) ("pending=" + $earn.Body.pending + " base=" + $baseEarnPend)
Check 'K2 confirmedToday unchanged by PENDING order' ([double]$earn.Body.confirmedToday -eq $baseConf) ("conf=" + $earn.Body.confirmedToday + " base=" + $baseConf)

# ===== L) Cancel -> excluded from revenue, aggregates, earnings =====
$cancel = Api $aarti 'PATCH' ("/api/seller/orders/" + $orderId + "/status") @{ orderStatus = 'CANCELLED' }
Check 'L1 seller cancels order' (($cancel.Status -eq 200) -and ($cancel.Body.orderStatus -eq 'CANCELLED')) ("status=" + $cancel.Status)
$sum2 = Api $aarti 'GET' '/api/seller-app/orders/summary' $null
Check 'L2 revenue back to baseline (cancelled excluded)' ([double]$sum2.Body.totalRevenue -eq $baseRev) ("rev=" + $sum2.Body.totalRevenue + " base=" + $baseRev)
Check 'L2b cancelledCount delta +1' ([int]$sum2.Body.cancelledCount -eq ($baseCanc + 1)) ("canc=" + $sum2.Body.cancelledCount + " base=" + $baseCanc)
$pohaAgg2 = @($sum2.Body.products) | Where-Object { $_.productId -eq $poha.id } | Select-Object -First 1
$L3ok = (($null -eq $pohaAgg2) -and ($aggBasePlates -eq 0)) -or (($null -ne $pohaAgg2) -and ([int]$pohaAgg2.totalPlates -eq $aggBasePlates))
Check 'L3 aggregate restored to baseline after cancel' $L3ok ("plates=" + $pohaAgg2.totalPlates + " base=" + $aggBasePlates)
$drill2 = Api $aarti 'GET' ("/api/seller-app/orders/product/" + $poha.id) $null
Check 'L4 drill: plates back to baseline, cancelled delta +1' (($drill2.Body.totalPlates -eq $basePlates) -and ([int]$drill2.Body.cancelledCount -eq ($baseCancel + 1))) ("plates=" + $drill2.Body.totalPlates + " base=" + $basePlates + " canc=" + $drill2.Body.cancelledCount)
Check 'L5 drill: pending back to baseline' ([int]$drill2.Body.pendingCount -eq $basePend) ("pend=" + $drill2.Body.pendingCount + " base=" + $basePend)
$earn2 = Api $aarti 'GET' '/api/seller-app/earnings' $null
Check 'L6 pending earnings back to baseline after cancel' ([double]$earn2.Body.pending -eq $baseEarnPend) ("pending=" + $earn2.Body.pending + " base=" + $baseEarnPend)
$dash3 = Api $aarti 'GET' '/api/seller-app/dashboard' $null
Check 'L7 dashboard earnings match baseline after cancel' (([double]$dash3.Body.pending -eq $baseEarnPend) -and ([double]$dash3.Body.confirmedToday -eq $baseConf)) ("pend=" + $dash3.Body.pending + " conf=" + $dash3.Body.confirmedToday)

# ===== M) Manual SOLD_OUT transition =====
$r = Api $aarti 'POST' ("/api/seller-app/products/" + $poha.id + "/sold-out") $null
Check 'M1 sold-out transition (remaining -> 0)' (($r.Status -eq 200) -and ([int]$r.Body.remainingQuantity -eq 0)) ("rem=" + $r.Body.remainingQuantity)

# ===== N) Hardening: validation, 404 mapping, upper/lower-bound inventory =====
$bad = Api $aarti 'POST' '/api/seller-app/templates' @{ name = 'E2E NegPrice'; description = 'x'; price = -5; priceUnit = 'plate' }
Check 'N1 negative price rejected 400' ($bad.Status -eq 400) ("status=" + $bad.Status)
$bad = Api $aarti 'POST' '/api/seller-app/templates' @{ name = ''; description = 'x'; price = 10; priceUnit = 'plate' }
Check 'N2 blank name rejected 400' ($bad.Status -eq 400) ("status=" + $bad.Status)
$bad = Api $aarti 'POST' '/api/seller-app/templates' @{ name = 'E2E BadCat'; description = 'x'; price = 10; priceUnit = 'plate'; category = 'NOT_A_CATEGORY' }
Check 'N3 invalid category rejected 400 with allowed list' (($bad.Status -eq 400) -and ($bad.Body.message -like '*Allowed values*')) ("status=" + $bad.Status)
$nf = Api $aarti 'DELETE' '/api/seller-app/templates/999999' $null
Check 'N4 unknown template delete -> 404 not 500' (($nf.Status -eq 404) -and ($nf.Body.error -eq 'NOT_FOUND')) ("status=" + $nf.Status)
$nf = Api $aarti 'POST' '/api/seller-app/templates/999999/publish' @{ availableDate = $today }
Check 'N5 unknown template publish -> 404 not 500' ($nf.Status -eq 404) ("status=" + $nf.Status)

# Capped template publishes at FULL stock (remaining = max); verify upper & lower bounds
$t5 = Api $aarti 'DELETE' ("/api/seller-app/templates/" + $t1.Body.id) $null
$null = Api $aarti 'DELETE' ("/api/seller-app/templates/" + $t2.Body.id) $null
$null = Api $aarti 'DELETE' ("/api/seller-app/templates/" + $t3.Body.id) $null
$t5 = Api $aarti 'POST' '/api/seller-app/templates' @{ name = 'E2E Capped'; description = 'x'; price = 40; priceUnit = 'plate'; maxQuantity = 2 }
$pub5 = Api $aarti 'POST' ("/api/seller-app/templates/" + $t5.Body.id + "/publish") @{ availableDate = $today }
Check 'N6 publish capped template (max=2, remaining=2 full stock)' (($pub5.Status -eq 200) -and ([int]$pub5.Body.maxQuantity -eq 2) -and ([int]$pub5.Body.remainingQuantity -eq 2)) ("max=" + $pub5.Body.maxQuantity + " rem=" + $pub5.Body.remainingQuantity)
$r = Api $aarti 'PATCH' ("/api/seller-app/products/" + $pub5.Body.id + "/inventory") @{ delta = 1 }
Check 'N7 increment above max rejected 400' (($r.Status -eq 400) -and ([int]$pub5.Body.remainingQuantity -eq 2)) ("status=" + $r.Status)
$null = Api $aarti 'PATCH' ("/api/seller-app/products/" + $pub5.Body.id + "/inventory") @{ delta = -1 }
$null = Api $aarti 'PATCH' ("/api/seller-app/products/" + $pub5.Body.id + "/inventory") @{ delta = -1 }
$r = Api $aarti 'PATCH' ("/api/seller-app/products/" + $pub5.Body.id + "/inventory") @{ delta = -1 }
Check 'N8 decrement below zero rejected 400' ($r.Status -eq 400) ("status=" + $r.Status)

# ===== O) Buyer demo payment flow: PAID -> CONFIRMED, My Orders, Order Detail =====
# Restock Poha (section M left it sold out) so the paid-flow can order it
$stock2 = Api $aarti 'PATCH' ("/api/seller-app/products/" + $poha.id + "/inventory") @{ delta = 5 }
Check 'O0 restock Poha +5 accepted' (($stock2.Status -eq 200) -and ([int]$stock2.Body.remainingQuantity -ge 5)) ("status=" + $stock2.Status + " rem=" + $stock2.Body.remainingQuantity)
# Pre-place baselines for shared-state deltas
$sumPre = Api $aarti 'GET' '/api/seller-app/orders/summary' $null
$preRev = [double]$sumPre.Body.totalRevenue
$earnPre = Api $aarti 'GET' '/api/seller-app/earnings' $null
$preConf = [double]$earnPre.Body.confirmedToday
$drillPre = Api $aarti 'GET' ("/api/seller-app/orders/product/" + $poha.id) $null
$prePlates = [int]$drillPre.Body.totalPlates
$buyer2 = New-Sess
$null = Login $buyer2 '9100000088' 'Paid Buyer' 'Y-777'
$draft2 = Api $buyer2 'POST' ("/api/buyer/orders/draft?kitchenId=" + $kid) @(@{ productId = $poha.id; quantity = 1 })
Check 'O1 draft for paid flow created (total 40)' (($draft2.Status -eq 200) -and ([double]$draft2.Body.totalAmount -eq 40)) ("status=" + $draft2.Status + " total=" + $draft2.Body.totalAmount)
$paid = Api $buyer2 'POST' '/api/buyer/orders/place' @{ paymentStatus = 'PAID'; buyerDetails = @{ name = 'Paid Buyer'; mobileNumber = '9100000088'; flatHouseNumber = 'Y-777' }; customInstructions = 'demo upi payment' }
Check 'O2 demo payment: order placed PAID' (($paid.Status -eq 200) -and ($paid.Body.paymentStatus -eq 'PAID')) ("status=" + $paid.Status + " pay=" + $paid.Body.paymentStatus)
Check 'O3 demo payment: order auto-CONFIRMED' (($paid.Status -eq 200) -and ($paid.Body.orderStatus -eq 'CONFIRMED')) ("status=" + $paid.Body.orderStatus)
$paidId = $paid.Body.id
$placeAgain = Api $buyer2 'POST' '/api/buyer/orders/place' @{ paymentStatus = 'PAID' }
Check 'O4 idempotent: re-place without new draft rejected (no duplicate)' (($placeAgain.Status -eq 400) -or ($placeAgain.Status -eq 409)) ("status=" + $placeAgain.Status)
$my2 = Api $buyer2 'GET' '/api/buyer/orders/my' $null
$mine = @($my2.Body.active) | Where-Object { $_.id -eq $paidId } | Select-Object -First 1
Check 'O5 My Orders lists the paid order (active)' ($null -ne $mine) ("status=" + $my2.Status)
Check 'O6 My Orders entry shows PAID + CONFIRMED + total 40' (($null -ne $mine) -and ($mine.paymentStatus -eq 'PAID') -and ($mine.orderStatus -eq 'CONFIRMED') -and ([double]$mine.totalAmount -eq 40)) ("pay=" + $mine.paymentStatus + " st=" + $mine.orderStatus + " tot=" + $mine.totalAmount)
$det = Api $buyer2 'GET' ("/api/buyer/orders/" + $paidId) $null
Check 'O7 order detail 200 with item, qty, unit price' (($det.Status -eq 200) -and (@($det.Body.items)[0].productName -eq $poha.name) -and (@($det.Body.items)[0].quantity -eq 1) -and ([double](@($det.Body.items)[0].price) -eq 40)) ("status=" + $det.Status + " item=" + @($det.Body.items)[0].productName)
Check 'O8 order detail: kitchen + payment + remarks present' (($det.Status -eq 200) -and ($null -ne $det.Body.kitchen.displayName) -and ($det.Body.paymentStatus -eq 'PAID') -and ($det.Body.customInstructions -eq 'demo upi payment')) ("kitchen=" + $det.Body.kitchen.displayName + " remarks=" + $det.Body.customInstructions)
$nf2 = Api $buyer2 'GET' '/api/buyer/orders/999999' $null
Check 'O9 unknown order detail -> 404 not 500' (($nf2.Status -eq 404) -and ($nf2.Body.error -eq 'NOT_FOUND')) ("status=" + $nf2.Status)
$foreign = Api $buyer 'GET' ("/api/buyer/orders/" + $paidId) $null
Check 'O10 another buyer cannot read my order detail (404)' (($foreign.Status -eq 404) -or ($foreign.Status -eq 403)) ("status=" + $foreign.Status)
$payPatch = Api $buyer2 'PATCH' ("/api/buyer/orders/" + $paidId + "/payment-status") @{ paymentStatus = 'PENDING' }
Check 'O11 payment-status update endpoint works' (($payPatch.Status -eq 200) -and ($payPatch.Body.paymentStatus -eq 'PENDING')) ("status=" + $payPatch.Status)
$payBack = Api $buyer2 'PATCH' ("/api/buyer/orders/" + $paidId + "/payment-status") @{ paymentStatus = 'PAID' }
Check 'O12 payment-status restored to PAID' (($payBack.Status -eq 200) -and ($payBack.Body.paymentStatus -eq 'PAID')) ("status=" + $payBack.Status)
# Shared state: PAID/CONFIRMED order must move seller aggregates
$sumPost = Api $aarti 'GET' '/api/seller-app/orders/summary' $null
Check 'O13 seller revenue delta +40 after paid order' ([double]$sumPost.Body.totalRevenue -eq ($preRev + 40)) ("rev=" + $sumPost.Body.totalRevenue + " pre=" + $preRev)
$earnPost = Api $aarti 'GET' '/api/seller-app/earnings' $null
Check 'O14 seller confirmedToday delta +40 after paid order' ([double]$earnPost.Body.confirmedToday -eq ($preConf + 40)) ("conf=" + $earnPost.Body.confirmedToday + " pre=" + $preConf)
$drillPost = Api $aarti 'GET' ("/api/seller-app/orders/product/" + $poha.id) $null
Check 'O15 drill-down plates delta +1 after paid order' ([int]$drillPost.Body.totalPlates -eq ($prePlates + 1)) ("plates=" + $drillPost.Body.totalPlates + " pre=" + $prePlates)
$dashPost = Api $aarti 'GET' '/api/seller-app/dashboard' $null
Check 'O16 dashboard totalOrders includes the paid order' ([int]$dashPost.Body.totalOrders -ge 1) ("orders=" + $dashPost.Body.totalOrders)

Write-Output ("RESULT PASS=" + $script:pass + " FAIL=" + $script:fail)
if ($script:fail -gt 0) { exit 1 } else { exit 0 }

Write-Output ("RESULT PASS=" + $script:pass + " FAIL=" + $script:fail)
if ($script:fail -gt 0) { exit 1 } else { exit 0 }