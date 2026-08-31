$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8081'
$pass = 0; $fail = 0
function Check($name, $cond, $extra) {
  if ($cond) { $script:pass++; Write-Host "PASS  $name" }
  else { $script:fail++; Write-Host "FAIL  $name  $extra" }
}
$mobile = '9' + (Get-Random -Minimum 100000000 -Maximum 999999999)

# --- static assets ---
$idx = Invoke-WebRequest "$base/" -UseBasicParsing -SessionVariable s
Check 'GET / (SPA shell)' ($idx.StatusCode -eq 200 -and $idx.Content -match 'SocioMart')
$css = Invoke-WebRequest "$base/css/styles.css" -UseBasicParsing
Check 'GET /css/styles.css' ($css.StatusCode -eq 200)
$js = Invoke-WebRequest "$base/js/app.js" -UseBasicParsing
Check 'GET /js/app.js (full build)' ($js.StatusCode -eq 200 -and $js.Content.Contains('bindEvents'))

# --- public marketplace ---
$mkt = Invoke-RestMethod "$base/api/marketplace" -WebSession $s
Check 'GET /api/marketplace' ($mkt.kitchens.Count -ge 1 -and ($mkt.availableToday.Count + $mkt.popularProducts.Count + $mkt.newProducts.Count) -ge 1)
$kit = Invoke-RestMethod "$base/api/kitchens/aartiskitchen" -WebSession $s
Check 'GET /api/kitchens/aartiskitchen' ($kit.kitchen.displayName -ne $null -and $kit.products.Count -ge 1)
$sr = Invoke-RestMethod "$base/api/search?q=dosa" -WebSession $s
Check 'GET /api/search?q=dosa' ($sr.products.Count -ge 1 -or $sr.kitchens.Count -ge 1)

# --- OTP auth ---
$otp = Invoke-RestMethod -Method Post "$base/api/auth/otp/request" -WebSession $s -ContentType 'application/json' -Body ('{"mobileNumber":"' + $mobile + '"}')
Check 'POST /api/auth/otp/request (returns dev OTP)' ($otp.otp -match '^\d{4}$')
$me0 = Invoke-RestMethod "$base/api/auth/me" -WebSession $s
Check 'auth state before verify = anonymous' ($me0.authenticated -eq $false)
$auth = Invoke-RestMethod -Method Post "$base/api/auth/otp/verify" -WebSession $s -ContentType 'application/json' -Body ('{"mobileNumber":"' + $mobile + '","otpCode":"' + $otp.otp + '","name":"Test Buyer","flatHouseNumber":"A-101"}')
Check 'POST /api/auth/otp/verify' ($auth.authenticated -eq $true -and $auth.role -eq 'BUYER')
$me1 = Invoke-RestMethod "$base/api/auth/me" -WebSession $s
Check 'GET /api/auth/me (session persisted)' ($me1.authenticated -eq $true -and $me1.mobileNumber -eq $mobile)
# --- cart / draft ---
$prod = $kit.products[0]
$draft = Invoke-RestMethod -Method Post "$base/api/buyer/orders/draft?kitchenId=$($kit.kitchen.id)" -WebSession $s -ContentType 'application/json' -Body ('[{"productId":' + $prod.id + ',"quantity":2}]')
Check 'POST /api/buyer/orders/draft (add 2x item)' ($draft.items.Count -eq 1 -and $draft.items[0].quantity -eq 2)
$draft2 = Invoke-RestMethod -Method Post "$base/api/buyer/orders/draft?kitchenId=$($kit.kitchen.id)" -WebSession $s -ContentType 'application/json' -Body ('[{"productId":' + $prod.id + ',"quantity":3}]')
Check 'POST /api/buyer/orders/draft (update to 3x)' ($draft2.items[0].quantity -eq 3)
$g = Invoke-RestMethod "$base/api/buyer/orders/draft" -WebSession $s
Check 'GET /api/buyer/orders/draft (persisted)' ($g.items[0].quantity -eq 3)

# --- place order ---
$ord = Invoke-RestMethod -Method Post "$base/api/buyer/orders/place" -WebSession $s -ContentType 'application/json' -Body '{"buyerDetails":{"name":"Test Buyer","mobileNumber":"' + $mobile + '","society":"Pride World City","building":"Tower A","flatHouseNumber":"A-101"},"customInstructions":"Less spicy please"}'
Check 'POST /api/buyer/orders/place' ($ord.id -gt 0 -and $ord.orderStatus -eq 'PLACED' -and $ord.paymentStatus -eq 'PENDING')
Check 'order has items+total' ($ord.items.Count -eq 1 -and [double]$ord.totalAmount -gt 0)
$g2 = Invoke-RestMethod "$base/api/buyer/orders/draft" -WebSession $s
Check 'draft cleared after place' ($g2.items.Count -eq 0)

# --- payment ---
$paid = Invoke-RestMethod -Method Patch "$base/api/buyer/orders/$($ord.id)/payment-status" -WebSession $s -ContentType 'application/json' -Body '{"paymentStatus":"PAID"}'
Check 'PATCH payment-status PAID' ($paid.paymentStatus -eq 'PAID')

# --- buyer orders ---
$my = Invoke-RestMethod "$base/api/buyer/orders/my" -WebSession $s
$mine = @(); $my.PSObject.Properties | ForEach-Object { if ($_.Value -is [array]) { $mine += $_.Value } }
Check 'GET /api/buyer/orders/my (order listed)' ($mine.Count -ge 1 -and ($mine | Where-Object { $_.id -eq $ord.id }).Count -eq 1)
$det = Invoke-RestMethod "$base/api/buyer/orders/$($ord.id)" -WebSession $s
Check 'GET /api/buyer/orders/{id} (buyer nested)' ($det.buyer.name -eq 'Test Buyer' -and $det.buyer.flatHouseNumber -eq 'A-101')
Invoke-RestMethod -Method Post "$base/api/buyer/orders/$($ord.id)/rate?rating=5" -WebSession $s | Out-Null
Check 'POST /api/buyer/orders/{id}/rate' ($true)
$ro = Invoke-RestMethod -Method Post "$base/api/buyer/orders/$($ord.id)/reorder" -WebSession $s
Check 'POST /api/buyer/orders/{id}/reorder (draft refilled)' ($ro.items.Count -ge 1)
Invoke-RestMethod -Method Delete "$base/api/buyer/orders/draft" -WebSession $s | Out-Null
# --- profile ---
$pf = Invoke-RestMethod -Method Put "$base/api/buyer/profile" -WebSession $s -ContentType 'application/json' -Body '{"name":"Test Buyer 2","society":"Pride World City","building":"Tower B","flatHouseNumber":"B-202"}'
Check 'PUT /api/buyer/profile' ($pf.name -eq 'Test Buyer 2' -and $pf.building -eq 'Tower B')

# --- seller flow ---
$bs = Invoke-RestMethod -Method Post "$base/api/auth/become-seller" -WebSession $s
Check 'POST /api/auth/become-seller' ($bs.role -eq 'SELLER')
$k0 = Invoke-RestMethod "$base/api/seller/kitchen" -WebSession $s
Check 'GET /api/seller/kitchen (auto-created)' ($k0.id -ne $null)
$slug = ('testkitchen' + (Get-Random -Maximum 9999))
$kn = Invoke-RestMethod -Method Post "$base/api/seller/kitchen" -WebSession $s -ContentType 'application/json' -Body ('{"name":"' + $slug + '","displayName":"Test Kitchen","shortDescription":"Home style","society":"Pride World City","building":"Tower B","upiId":"test@upi","availableToday":true}')
Check 'POST /api/seller/kitchen (save profile)' ($kn.displayName -eq 'Test Kitchen' -and $kn.upiId -eq 'test@upi')
$np = Invoke-RestMethod -Method Post "$base/api/seller/products" -WebSession $s -ContentType 'application/json' -Body '{"name":"Test Pakora","description":"Crispy","price":40,"priceUnit":"plate","availableToday":true,"maxQuantity":10}'
Check 'POST /api/seller/products (create)' ($np.id -gt 0 -and $np.name -eq 'Test Pakora')
$up = Invoke-RestMethod -Method Put "$base/api/seller/products/$($np.id)" -WebSession $s -ContentType 'application/json' -Body ('{"id":' + $np.id + ',"name":"Test Pakora XL","description":"Crispy","price":50,"priceUnit":"plate","availableToday":false,"maxQuantity":10}')
Check 'PUT /api/seller/products/{id} (pause)' ($up.name -eq 'Test Pakora XL' -and $up.availableToday -eq $false)
$sp = Invoke-RestMethod "$base/api/seller/products" -WebSession $s
Check 'GET /api/seller/products (mine listed)' (($sp | Where-Object { $_.id -eq $np.id }).Count -eq 1)
# --- seller order management: second buyer session ---
$mobile2 = '8' + (Get-Random -Minimum 100000000 -Maximum 999999999)
Invoke-WebRequest "$base/" -UseBasicParsing -SessionVariable s2 | Out-Null
$ot2 = Invoke-RestMethod -Method Post "$base/api/auth/otp/request" -WebSession $s2 -ContentType 'application/json' -Body ('{"mobileNumber":"' + $mobile2 + '"}')
Invoke-RestMethod -Method Post "$base/api/auth/otp/verify" -WebSession $s2 -ContentType 'application/json' -Body ('{"mobileNumber":"' + $mobile2 + '","otpCode":"' + $ot2.otp + '","name":"Buyer Two"}') | Out-Null
Invoke-RestMethod -Method Post "$base/api/buyer/orders/draft?kitchenId=$($kn.id)" -WebSession $s2 -ContentType 'application/json' -Body ('[{"productId":' + $np.id + ',"quantity":1}]') | Out-Null
$ord2 = Invoke-RestMethod -Method Post "$base/api/buyer/orders/place" -WebSession $s2 -ContentType 'application/json' -Body '{"buyerDetails":{"name":"Buyer Two","mobileNumber":"' + $mobile2 + '","flatHouseNumber":"C-303"}}'
Check 'second buyer placed order on new product' ($ord2.id -gt 0)
$so = Invoke-RestMethod "$base/api/seller/orders" -WebSession $s
$allSo = @(); $so.PSObject.Properties | ForEach-Object { if ($_.Value -is [array]) { $allSo += $_.Value } }
Check 'GET /api/seller/orders (seller sees order)' (($allSo | Where-Object { $_.id -eq $ord2.id }).Count -ge 1)
$st = Invoke-RestMethod -Method Patch "$base/api/seller/orders/$($ord2.id)/status" -WebSession $s -ContentType 'application/json' -Body '{"orderStatus":"CONFIRMED"}'
Check 'PATCH /api/seller/orders/{id}/status' ($st.orderStatus -eq 'CONFIRMED')
Invoke-RestMethod -Method Delete "$base/api/seller/products/$($np.id)" -WebSession $s | Out-Null
Check 'DELETE /api/seller/products/{id}' ($true)

# --- logout ---
Invoke-RestMethod -Method Post "$base/api/auth/logout" -WebSession $s | Out-Null
$me2 = Invoke-RestMethod "$base/api/auth/me" -WebSession $s
Check 'POST /api/auth/logout' ($me2.authenticated -eq $false)

Write-Host ''
Write-Host "RESULT: $pass passed, $fail failed"
if ($fail -gt 0) { exit 1 }



